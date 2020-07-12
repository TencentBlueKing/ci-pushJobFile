package com.tencent.bk.devops.atom.task.utils

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.tls.HandshakeCertificates
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.security.KeyStore
import java.security.GeneralSecurityException
import java.security.cert.CertificateFactory
import java.util.*
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


object OkhttpUtils {

    private val logger = LoggerFactory.getLogger(OkhttpUtils::class.java)

    val certificateStrList = mutableListOf<String>()

    private var okHttpClient: OkHttpClient? = null

    // 下载会出现从 文件源--（耗时长）---->网关（网关全部收完才转发给用户，所以用户侧与网关存在读超时的可能)-->用户
    private var longHttpClient: OkHttpClient? = null

    fun addCertificate(certificateStr: String) {
        certificateStrList.add(certificateStr)
    }

    fun getOkHttpClient(): OkHttpClient {
        if (okHttpClient == null) {
            var allCertificatesStr = ""
            certificateStrList.forEach { certificateStr ->
                allCertificatesStr += certificateStr + "\n"
            }
            allCertificatesStr.removeSuffix("\n")
            var trustManager: X509TrustManager? = null
            var sslSocketFactory: SSLSocketFactory? = null
            try {
                trustManager = trustManagerForCertificates(allCertificatesStr.byteInputStream())
                val sslContext = SSLContext.getInstance("TLS")
                //使用构建出的trustManger初始化SSLContext对象
                sslContext.init(null, arrayOf(trustManager), null)
                //获得sslSocketFactory对象
                sslSocketFactory = sslContext.socketFactory
            } catch (e: Exception) {
                logger.info("Fail to load certificate")
            }
            val builder = okhttp3.OkHttpClient.Builder()
            if (sslSocketFactory != null && trustManager != null) {
                builder.sslSocketFactory(sslSocketFactory, trustManager)
            }
            okHttpClient = builder
                .connectTimeout(5L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.SECONDS)
                .writeTimeout(30L, TimeUnit.SECONDS)
                .build()!!
        }
        return okHttpClient!!
    }

    fun getLongClient(): OkHttpClient {
        if (longHttpClient == null) {
            var allCertificatesStr = ""
            certificateStrList.forEach { certificateStr ->
                allCertificatesStr += certificateStr + "\n"
            }
            allCertificatesStr.removeSuffix("\n")
            val trustManager = trustManagerForCertificates(allCertificatesStr.byteInputStream())
            val sslContext = SSLContext.getInstance("TLS")
            //使用构建出的trustManger初始化SSLContext对象
            sslContext.init(null, arrayOf(trustManager), null)
            //获得sslSocketFactory对象
            val sslSocketFactory = sslContext.socketFactory
            longHttpClient = okhttp3.OkHttpClient.Builder()
                .sslSocketFactory(sslSocketFactory, trustManager)
                .connectTimeout(5L, TimeUnit.SECONDS)
                .readTimeout(30L, TimeUnit.MINUTES)
                .writeTimeout(30L, TimeUnit.MINUTES)
                .build()!!
        }
        return longHttpClient!!
    }

    @Throws(UnsupportedEncodingException::class)
    fun joinParams(params: Map<String, String>): String {
        val paramItem = ArrayList<String>()
        for ((key, value) in params) {
            paramItem.add(key + "=" + URLEncoder.encode(value, "UTF-8"))
        }
        return paramItem.joinToString("&")
    }

    fun doGet(url: String, headers: Map<String, String> = mapOf()): Response {
        return doGet(getOkHttpClient(), url, headers)
    }

    fun doHttp(request: Request): Response {
        return doHttp(getOkHttpClient(), request)
    }

    private fun doGet(okHttpClient: OkHttpClient, url: String, headers: Map<String, String> = mapOf()): Response {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
        if (headers.isNotEmpty()) {
            headers.forEach { key, value ->
                requestBuilder.addHeader(key, value)
            }
        }
        val request = requestBuilder.build()
        return okHttpClient.newCall(request).execute()
    }

    private fun doHttp(okHttpClient: OkHttpClient, request: Request): Response {
        return okHttpClient.newCall(request).execute()
    }

    fun downloadFile(url: String, destPath: File) {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        getLongClient().newCall(request).execute().use { response ->
            if (response.code() == 404) {
                logger.warn("The file $url is not exist")
                throw RuntimeException("文件不存在")
            }
            if (!response.isSuccessful) {
                logger.warn("fail to download the file from $url because of ${response.message()} and code ${response.code()}")
                throw RuntimeException("获取文件失败")
            }
            if (!destPath.parentFile.exists()) {
                destPath.parentFile.mkdirs()
                logger.info("mkdir file:${destPath.parentFile.name}")
            }
            val buf = ByteArray(4096)
            response.body()!!.byteStream().use { bs ->
                var len = bs.read(buf)
                FileOutputStream(destPath).use { fos ->
                    while (len != -1) {
                        fos.write(buf, 0, len)
                        len = bs.read(buf)
                    }
                }
            }
        }
    }

    fun downloadFile(response: Response, destPath: File) {
        if (response.code() == 304) {
            logger.info("file is newest, do not download to $destPath")
            return
        }
        if (!response.isSuccessful) {
            logger.warn("fail to download the file because of ${response.message()} and code ${response.code()}")
            throw RuntimeException("获取文件失败")
        }
        if (!destPath.parentFile.exists()) destPath.parentFile.mkdirs()
        val buf = ByteArray(4096)
        response.body()!!.byteStream().use { bs ->
            var len = bs.read(buf)
            FileOutputStream(destPath).use { fos ->
                while (len != -1) {
                    fos.write(buf, 0, len)
                    len = bs.read(buf)
                }
            }
        }
    }

    /**
     * 对外提供的获取支持自签名的okhttp客户端
     *
     * @param certificate 自签名证书的输入流
     * @return 支持自签名的客户端
     */
    fun getTrusClient(certificate: InputStream): OkHttpClient {
        val trustManager: X509TrustManager
        val sslSocketFactory: SSLSocketFactory
        try {
            trustManager = trustManagerForCertificates(certificate)
            val sslContext = SSLContext.getInstance("TLS")
            //使用构建出的trustManger初始化SSLContext对象
            sslContext.init(null, arrayOf<TrustManager>(trustManager), null)
            //获得sslSocketFactory对象
            sslSocketFactory = sslContext.getSocketFactory()
        } catch (e: GeneralSecurityException) {
            throw RuntimeException(e)
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .build()
    }

    /**
     * 获去信任自签证书的trustManager
     *
     * @param in 自签证书输入流
     * @return 信任自签证书的trustManager
     * @throws GeneralSecurityException
     */
    @Throws(GeneralSecurityException::class)
    private fun trustManagerForCertificates(`in`: InputStream): X509TrustManager {
        val certificateFactory = CertificateFactory.getInstance("X.509")
        //通过证书工厂得到自签证书对象集合
        val certificates = certificateFactory.generateCertificates(`in`)
        if (certificates.isEmpty()) {
            throw IllegalArgumentException("expected non-empty set of trusted certificates")
        }
        //为证书设置一个keyStore
        val password = "password".toCharArray() // Any password will work.
        val keyStore = newEmptyKeyStore(password)
        var index = 0
        //将证书放入keystore中
        for (certificate in certificates) {
            val certificateAlias = Integer.toString(index++)
            keyStore.setCertificateEntry(certificateAlias, certificate)
        }
        // Use it to build an X509 trust manager.
        //使用包含自签证书信息的keyStore去构建一个X509TrustManager
        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm())
        keyManagerFactory.init(keyStore, password)
        val trustManagerFactory = TrustManagerFactory.getInstance(
            TrustManagerFactory.getDefaultAlgorithm())
        trustManagerFactory.init(keyStore)
        val trustManagers = trustManagerFactory.getTrustManagers()
        if (trustManagers.size != 1 || trustManagers[0] !is X509TrustManager) {
            throw IllegalStateException("Unexpected default trust managers:" + Arrays.toString(trustManagers))
        }
        return trustManagers[0] as X509TrustManager
    }

    fun newEmptyKeyStore(password: CharArray): KeyStore {
        try {
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
            val ins: InputStream? = null // By convention, 'null' creates an empty key store.
            keyStore.load(null, password)
            return keyStore
        } catch (e: IOException) {
            throw AssertionError(e)
        }
    }
}