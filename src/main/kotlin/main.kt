import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import khttp.get
import khttp.post
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.regex.Pattern
import kotlin.system.exitProcess
import java.net.URLDecoder
import java.util.LinkedHashMap
import java.io.UnsupportedEncodingException
import java.net.URL


@Suppress("PrivatePropertyName")
class VKSpider(val email: String, val pass: String) {
    val APP_ID = 6708705
    private val CLIENT_SECRET = "TqkWrY3M3fqXMSMsjXrH"
    val REDIRECT_URI = "https://oauth.vk.com/blank.html"
    private val vk = init()
//    private val code = appAuthorization()
//    val actor = userAuthorization(code)

    private fun init(): VkApiClient {
        val transportClient = HttpTransportClient.getInstance()
        return VkApiClient(transportClient)
    }

    private fun appAuthorization() = getCode()

    private fun userAuthorization(code: String): UserActor {
        val authResponse = vk.oauth()
                .userAuthorizationCodeFlow(APP_ID, CLIENT_SECRET, REDIRECT_URI, code)
                .execute()
        return UserActor(authResponse.userId, authResponse.accessToken)
    }

    fun parse(html: String, p: Pattern): String {
        val m = p.matcher(html)
        if (m.find()) {
            return html.substring(m.start(), m.end())
        }
        return ""
    }

    fun parseBlock(html: String, tag: String) = parse(html, Pattern.compile("<$tag[\\W]*[^>]*>.*</$tag>"))

    fun parseTag(html: String, tag: String)= parse(html, Pattern.compile("<$tag[\\W]*[^>]*>"))

    fun parseParam(html: String, param: String): String? {
        val p = Pattern.compile("$param=\"([^\"]*)\"")
        val m = p.matcher(html.replace("\n", ""))
        if (m.find()) {
            return m.group(1) ?: ""
        }
        return ""
    }

    fun parseInputs(form: String): List<String> {
        var f = form
        val inputs = mutableListOf<String>()
        do {
            val input = parseTag(f, "input")
            inputs.add(input)
            f = f.replace(input, "")
        } while (!input.isBlank())
        return inputs
    }

    fun extractPostData(inputs: List<String>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        inputs.forEach {
            val name = parseParam(it, "name")!!
            val value = parseParam(it, "value")!!
            map[name] = value
        }
        return map
    }

    fun getLoginData(html: String, email: String, pass: String): Map<String, String> {
        val form = parseBlock(html.replace("\n", ""), "form")
        val inputs = parseInputs(form)
        val data = mutableMapOf<String, String>()
        val d = extractPostData(inputs.filter { it.contains("hidden") })
        data["ip_h"] = d["ip_h"]!!
        data["lg_h"] = d["lg_h"]!!
        data["_origin"] = d["_origin"]!!
        data["to"] = d["to"]!!
        data["expire"] = "0"
        data["email"] = email
        data["pass"] = pass
        return data
    }

    fun getCode(): String {
        val url = "https://oauth.vk.com/authorize?client_id=$APP_ID&redirect_uri=$REDIRECT_URI&display=page&scope=friends&response_type=code&v=5.85"
        var response = get(url)
        val data = getLoginData(response.text, email, pass)
        var cookies = response.cookies.toMap()
        response = post("https://login.vk.com?act=login&soft=1", cookies = cookies, data = data)
        cookies = response.cookies.toMap()
        val tag = parseTag(response.text, "form")
        val action = parseParam(tag, "action")!!
        response = get(action, cookies=cookies)
        return response.url.split("#code=").last()
    }
}

fun readFromFile(fname: String): String {
    var html = ""
    FileReader(File(fname)).use {
        val lines = it.readLines()
        html = lines.reduce { acc, s -> acc + "$s\n" }
    }
    return html
}

fun main(args: Array<String>) {
    val spider = VKSpider("89266552375", "m31k0l2")
    println(spider.getCode())
}