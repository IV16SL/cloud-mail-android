package me.huanjue.cloudmail.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.bcpg.ArmoredInputStream
import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPCompressedData
import org.bouncycastle.openpgp.PGPEncryptedData
import org.bouncycastle.openpgp.PGPEncryptedDataList
import org.bouncycastle.openpgp.PGPKeyPair
import org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.bouncycastle.openpgp.PGPLiteralData
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPrivateKey
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSecretKeyRing
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcePublicKeyDataDecryptorFactoryBuilder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.security.Security
import java.util.Date

class PgpException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 钥匙信息（展示用） */
data class PgpKeyInfo(
    val userId: String,      // 钥匙上的用户 ID，一般是 姓名 <邮箱>
    val fingerprint: String, // 指纹（大写 hex）
    val keyId: String        // key id（hex）
)

/**
 * PGP 管理：BouncyCastle 纯 Java 实现，内置在 App 里，不依赖 OpenKeychain 等外部应用。
 *
 * 分工（对标网页版）：
 * - 发信加密走服务端：写信时传 pgpEncrypt=true，后端用 openpgp 按收件人公钥加密，
 *   App 只负责查收件人公钥状态（/pgp/key-status）做前端拦截。
 * - 收信解密在端侧：私钥只存本机（EncryptedSharedPreferences），绝不上传，
 *   解密必须在 App 里做，这就是 BouncyCastle 的用处。
 */
class PgpManager(private val context: Context, private val settings: AppSettings) {

    companion object {
        const val ARMOR_BEGIN = "-----BEGIN PGP MESSAGE-----"
        private const val ARMOR_END = "-----END PGP MESSAGE-----"
        private const val SECRET_ARMOR_BEGIN = "-----BEGIN PGP PRIVATE KEY BLOCK-----"

        init {
            // Android 自带一个阉割版 BC（同名 "BC"），把完整版插到第 1 位盖住它，
            // 否则 JcePBESecretKeyDecryptorBuilder 等会解析到系统阉割版而缺算法（如 SHA1）。
            // 注意：不能只检查 !is BouncyCastleProvider，因为系统阉割版的类名也可能叫 BouncyCastleProvider，
            // 必须确保我们 jar 里的完整版在第 1 位。
            val existing = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (existing == null || existing.javaClass.name != BouncyCastleProvider::class.java.name ||
                existing::class.java.classLoader != BouncyCastleProvider::class.java.classLoader
            ) {
                // 移除已有的（可能是系统阉割版），再插入完整版到第 1 位
                if (existing != null) Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        }

        /** 拿到我们 jar 里的完整版 BC Provider 实例，避免 setProvider("BC") 解析到系统阉割版 */
        fun bcProvider(): java.security.Provider =
            Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
                ?: BouncyCastleProvider().also { Security.insertProviderAt(it, 1) }

        /** 文本里是否包含 PGP 密文（列表页绿锁判断用） */
        fun containsEncryptedData(text: String?): Boolean =
            text != null && text.contains(ARMOR_BEGIN)

        /** 从文本（可能混有其他内容/HTML）里提取第一个 PGP MESSAGE armor 块 */
        fun extractArmorBlock(text: String): String? {
            val start = text.indexOf(ARMOR_BEGIN)
            if (start < 0) return null
            val end = text.indexOf(ARMOR_END, start)
            if (end < 0) return null
            return text.substring(start, end + ARMOR_END.length)
        }

        /** 粗糙去 HTML 标签（armor 块提取用，armor 里没有 <） */
        fun stripHtml(html: String): String =
            html.replace(Regex("<[^>]*>"), "")
    }

    private fun encryptedPrefs(): SharedPreferences {
        // security-crypto 1.0.0（stable）的 API：MasterKeys + 别名，
        // 注意参数顺序是 (name, masterKeyAlias, context, ...)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            "pgp_keys",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private var cachedUserId: Long? = null

    private suspend fun activeUserId(): Long {
        cachedUserId?.let { if (it != 0L) return it }
        val uid = settings.getActiveSession()?.userId ?: 0L
        if (uid != 0L) cachedUserId = uid
        return uid
    }

    /** 切换账号/登出时清掉缓存的 userId，下次重新从 DataStore 读 */
    fun clearUserIdCache() {
        cachedUserId = null
    }

    private fun armorKey(userId: Long) = "private_key_armor_$userId"
    private fun protectedKey(userId: Long) = "private_key_protected_$userId"

    suspend fun hasPrivateKey(): Boolean = withContext(Dispatchers.IO) {
        !encryptedPrefs().getString(armorKey(activeUserId()), null).isNullOrBlank()
    }

    suspend fun isProtectedKey(): Boolean = withContext(Dispatchers.IO) {
        // 先读导入时存的 flag；如果找不到（比如导入时 userId 不对），动态检测
        val uid = activeUserId()
        if (encryptedPrefs().contains(protectedKey(uid))) {
            return@withContext encryptedPrefs().getBoolean(protectedKey(uid), false)
        }
        // 动态检测：试所有子钥匙的空口令，有一把要口令就算有保护
        val armor = encryptedPrefs().getString(armorKey(uid), null) ?: return@withContext false
        val keys = parseSecretKeys(armor)
        val protected = keys.any { sk ->
            try {
                extractPrivateKey(sk, CharArray(0))
                false
            } catch (_: Exception) {
                true
            }
        }
        // 顺手把检测结果存回去，下次直接读
        encryptedPrefs().edit().putBoolean(protectedKey(uid), protected).apply()
        protected
    }

    suspend fun getKeyInfo(): PgpKeyInfo? = withContext(Dispatchers.IO) {
        val armor = encryptedPrefs().getString(armorKey(activeUserId()), null)
            ?: return@withContext null
        parseSecretKeys(armor).firstOrNull { it.isMasterKey }
            ?.let { keyInfoOf(it) }
            ?: parseSecretKeys(armor).firstOrNull()?.let { keyInfoOf(it) }
    }

    /**
     * 导入私钥 armor 文本。校验通过后加密存到本地（按登录账号隔离）。
     * @return 钥匙信息（展示指纹用）
     */
    suspend fun importPrivateKey(armored: String): PgpKeyInfo =
        withContext(Dispatchers.IO) {
            val armor = armored.trim()
            require(armor.contains(SECRET_ARMOR_BEGIN)) {
                context.getString(me.huanjue.cloudmail.R.string.pgp_err_invalid_key)
            }
            val keys = parseSecretKeys(armor)
            require(keys.isNotEmpty()) { context.getString(me.huanjue.cloudmail.R.string.pgp_err_parse_failed) }
            val master = keys.firstOrNull { it.isMasterKey } ?: keys.first()
            // 导入时试一次空口令，判断这把钥匙是否设了口令（解密时决定要不要弹窗）
            // 注意：要测所有子钥匙，不能只测 master——加密用的往往是子钥匙，
            // master 无口令但子钥匙有口令的情况很常见
            val protected = keys.any { sk ->
                try {
                    extractPrivateKey(sk, CharArray(0))
                    false
                } catch (_: Exception) {
                    true
                }
            }
            val uid = activeUserId()
            encryptedPrefs().edit()
                .putString(armorKey(uid), armor)
                .putBoolean(protectedKey(uid), protected)
                .apply()
            keyInfoOf(master)
        }

    suspend fun deletePrivateKey() = withContext(Dispatchers.IO) {
        val uid = activeUserId()
        encryptedPrefs().edit()
            .remove(armorKey(uid))
            .remove(protectedKey(uid))
            .apply()
    }

    /**
     * 在 App 内生成新的 PGP 密钥对（签名主钥 + 加密子钥）。
     * keyType: "RSA"（RSA 3072，兼容性最好）或 "ED25519"（Ed25519 签名 + X25519 加密，更小更快）
     * 生成的私钥自动导入（存本机），公钥 armor 一并返回，方便复制去发布
     *（比如 keys.openpgp.org），否则别人查不到你的公钥、没法给你发加密邮件。
     * @param passphrase 口令；传 null 或空表示无口令
     * @return Pair(钥匙信息, 公钥 armor 文本)
     */
    suspend fun generateKeyPair(
        name: String,
        email: String,
        passphrase: CharArray?,
        keyType: String = "RSA"
    ): Pair<PgpKeyInfo, String> = withContext(Dispatchers.IO) {
        val userId = if (name.isBlank()) email.trim() else "${name.trim()} <${email.trim()}>"
        if (email.isBlank()) throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_email_empty))

        val sha1Calc = JcaPGPDigestCalculatorProviderBuilder().build()
            .get(HashAlgorithmTags.SHA1)

        val signingKeyPair: JcaPGPKeyPair
        val encryptionKeyPair: JcaPGPKeyPair
        if (keyType == "ED25519") {
            val edGen = java.security.KeyPairGenerator.getInstance("Ed25519", bcProvider())
            @Suppress("DEPRECATION")
            val eddsaAlg = PGPPublicKey.EDDSA
            signingKeyPair = JcaPGPKeyPair(
                eddsaAlg, edGen.generateKeyPair(), Date()
            )
            val xGen = java.security.KeyPairGenerator.getInstance("X25519", bcProvider())
            encryptionKeyPair = JcaPGPKeyPair(
                PGPPublicKey.ECDH, xGen.generateKeyPair(), Date()
            )
        } else {
            val keyGen = java.security.KeyPairGenerator.getInstance("RSA", bcProvider())
            keyGen.initialize(3072, SecureRandom())
            signingKeyPair = JcaPGPKeyPair(
                PGPPublicKey.RSA_SIGN, keyGen.generateKeyPair(), Date()
            )
            encryptionKeyPair = JcaPGPKeyPair(
                PGPPublicKey.RSA_ENCRYPT, keyGen.generateKeyPair(), Date()
            )
        }
        val encryptor = JcePBESecretKeyEncryptorBuilder(
            PGPEncryptedData.AES_256, sha1Calc
        ).setProvider(bcProvider()).build(passphrase?.takeIf { it.isNotEmpty() } ?: CharArray(0))

        val keyRingGen = PGPKeyRingGenerator(
            PGPSignature.POSITIVE_CERTIFICATION,
            signingKeyPair,
            userId,
            sha1Calc,
            null, null,
            JcaPGPContentSignerBuilder(
                signingKeyPair.publicKey.algorithm, HashAlgorithmTags.SHA256
            ).setProvider(bcProvider()),
            encryptor
        )
        keyRingGen.addSubKey(encryptionKeyPair)

        val secretKeyRing = keyRingGen.generateSecretKeyRing()
        val publicKeyRing = keyRingGen.generatePublicKeyRing()

        val privateArmor = ByteArrayOutputStream().use { bos ->
            ArmoredOutputStream(bos).use { out -> secretKeyRing.encode(out) }
            bos.toString(Charsets.UTF_8.name())
        }
        val publicArmor = ByteArrayOutputStream().use { bos ->
            ArmoredOutputStream(bos).use { out -> publicKeyRing.encode(out) }
            bos.toString(Charsets.UTF_8.name())
        }

        val info = importPrivateKey(privateArmor)
        Pair(info, publicArmor)
    }

    /** PGP 功能总开关（按登录用户隔离，默认开） */
    suspend fun isPgpEnabled(): Boolean = withContext(Dispatchers.IO) {
        encryptedPrefs().getBoolean("pgp_enabled_${activeUserId()}", true)
    }

    suspend fun setPgpEnabled(enabled: Boolean) = withContext(Dispatchers.IO) {
        encryptedPrefs().edit().putBoolean("pgp_enabled_${activeUserId()}", enabled).apply()
    }

    /**
     * 解密 PGP 消息。
     * @param messageOrText 完整的 armor 文本，或包含 armor 的邮件正文（自动提取）
     * @param passphrase 私钥口令；无口令钥匙传 null
     * @return 解密出的明文
     */
    suspend fun decrypt(messageOrText: String, passphrase: CharArray?): String =
        withContext(Dispatchers.IO) {
            val armor = extractArmorBlock(messageOrText)
                ?: throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_no_ciphertext))
            val keyArmor = encryptedPrefs()
                .getString(armorKey(activeUserId()), null)
                ?: throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_no_private_key))
            val secretKeys = parseSecretKeys(keyArmor)
            if (secretKeys.isEmpty()) throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_parse_failed))

            val factory = PGPObjectFactory(
                ArmoredInputStream(ByteArrayInputStream(armor.toByteArray(Charsets.UTF_8))),
                JcaKeyFingerprintCalculator()
            )
            // 跳过可能存在的 marker 包，找到加密数据包列表
            //（不写显式可空类型，让 ?: throw 把类型收窄为非空，避免智能转换失效）
            val encList = try {
                var found: PGPEncryptedDataList? = null
                var obj: Any? = factory.nextObject()
                while (obj != null) {
                    if (obj is PGPEncryptedDataList) {
                        found = obj
                        break
                    }
                    obj = factory.nextObject()
                }
                found
            } catch (_: Exception) {
                null
            } ?: throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_bad_format))

            val pw = passphrase ?: CharArray(0)
            // 诊断：只记口令长度，不记内容
            android.util.Log.w("PgpManager", "decrypt pwLen=${pw.size}")
            var lastErr: Exception? = null
            var matchedKey = false
            var matchedSk: org.bouncycastle.openpgp.PGPSecretKey? = null
            // 收集诊断信息：邮件是加密给哪些 keyID 的，本地有哪些 keyID
            val emailKeyIds = encList.encryptedDataObjects.asSequence()
                .filterIsInstance<PGPPublicKeyEncryptedData>()
                .map { "%016X".format(it.keyID) }
                .toList()
            val localKeyIds = secretKeys.map { "%016X".format(it.keyID) }
            // 逐把钥匙试：加密可能用的是子钥匙，不能只认 master key
            // 口令候选：用户输入的优先，空口令兜底（防止导入时误判保护状态）
            val pwCandidates = if (pw.isNotEmpty()) listOf(pw, CharArray(0)) else listOf(pw)
            for (sk in secretKeys) {
                val encData = encList.encryptedDataObjects.asSequence()
                    .filterIsInstance<PGPPublicKeyEncryptedData>()
                    .firstOrNull { it.keyID == sk.keyID }
                    ?: continue
                matchedKey = true
                matchedSk = sk
                // 诊断：打印这把钥匙的 S2K 参数（哈希、加密算法、S2K 类型），定位 checksum mismatch 原因
                try {
                    val s2k = sk.s2K
                    android.util.Log.w(
                        "PgpManager",
                        "key ${"%016X".format(sk.keyID)} s2kType=${s2k?.type} hash=${s2k?.hashAlgorithm} encAlg=${sk.keyEncryptionAlgorithm}"
                    )
                } catch (_: Exception) {
                }
                for ((pwIdx, candidate) in pwCandidates.withIndex()) {
                    try {
                        val privateKey = extractPrivateKey(sk, candidate)
                        val clear = encData.getDataStream(
                            JcePublicKeyDataDecryptorFactoryBuilder()
                                .setProvider(bcProvider())
                                .build(privateKey)
                        )
                        return@withContext readLiteral(clear)
                    } catch (e: Exception) {
                        // 记录详细异常：类名 + message，帮助定位是口令错还是算法不支持
                        android.util.Log.w(
                            "PgpManager",
                            "decrypt failed key=${"%016X".format(sk.keyID)} pwIdx=$pwIdx err=${e.javaClass.simpleName}: ${e.message}"
                        )
                        lastErr = e
                    }
                }
            }
            // 诊断信息：帮助用户判断是钥匙不对还是口令不对
            val diag = " (email keys: ${emailKeyIds.joinToString(",")}; local keys: ${localKeyIds.joinToString(",")})"
            val errDetail = lastErr?.let { " [${it.javaClass.simpleName}: ${it.message}]" } ?: ""
            val s2kDetail = try {
                matchedSk?.let { sk ->
                    val s2k = sk.s2K
                    " [s2kType=${s2k?.type} hash=${s2k?.hashAlgorithm} enc=${sk.keyEncryptionAlgorithm} pwLen=${pw.size}]"
                }
            } catch (_: Exception) { null } ?: ""
            throw PgpException(
                if (!matchedKey) context.getString(me.huanjue.cloudmail.R.string.pgp_err_wrong_key) + diag + errDetail + s2kDetail
                else context.getString(me.huanjue.cloudmail.R.string.pgp_err_wrong_passphrase) + diag + errDetail + s2kDetail,
                lastErr
            )
        }

    // ---------- 内部 ----------

    private fun parseSecretKeys(armor: String): List<PGPSecretKey> {
        val out = mutableListOf<PGPSecretKey>()
        try {
            val rings = PGPSecretKeyRingCollection(
                ArmoredInputStream(ByteArrayInputStream(armor.toByteArray(Charsets.UTF_8))),
                JcaKeyFingerprintCalculator()
            )
            val ringIt = rings.keyRings
            while (ringIt.hasNext()) {
                val keyIt = (ringIt.next() as PGPSecretKeyRing).secretKeys
                while (keyIt.hasNext()) out += keyIt.next() as PGPSecretKey
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun extractPrivateKey(
        secretKey: PGPSecretKey,
        passphrase: CharArray
    ): PGPPrivateKey =
        secretKey.extractPrivateKey(
            JcePBESecretKeyDecryptorBuilder().setProvider(bcProvider()).build(passphrase)
        )

    private fun keyInfoOf(key: PGPSecretKey): PgpKeyInfo {
        val fp = key.publicKey.fingerprint.joinToString("") { "%02X".format(it) }
        val userId = key.userIDs.asSequence().firstOrNull() ?: ""
        val keyId = "%016X".format(key.keyID)
        return PgpKeyInfo(userId, fp, keyId)
    }

    private fun readLiteral(clear: InputStream): String {
        var obj: Any? = PGPObjectFactory(clear, JcaKeyFingerprintCalculator()).nextObject()
        if (obj is PGPCompressedData) {
            obj = PGPObjectFactory(obj.dataStream, JcaKeyFingerprintCalculator()).nextObject()
        }
        val literal = obj as? PGPLiteralData ?: throw PgpException(context.getString(me.huanjue.cloudmail.R.string.pgp_err_not_text))
        return literal.inputStream.readBytes().toString(Charsets.UTF_8)
    }
}
