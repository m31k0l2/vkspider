import com.vk.api.sdk.client.VkApiClient
import com.vk.api.sdk.client.actors.UserActor
import com.vk.api.sdk.httpclient.HttpTransportClient
import khttp.get
import khttp.post
import java.io.File
import java.io.FileReader
import java.util.*
import java.util.regex.Pattern


@Suppress("PrivatePropertyName")
class VKSpider(val email: String, val pass: String, var userId: Int = 0, var accessToken: String = "") {
    val APP_ID = 6713164
    private val CLIENT_SECRET = "grMqIuj8hIlFZULsrXDw"
    val REDIRECT_URI = "https://oauth.vk.com/blank.html"
    private val vk = init()
//    private val code = appAuthorization()

    private fun init(): VkApiClient {
        val transportClient = HttpTransportClient.getInstance()
        return VkApiClient(transportClient)
    }

    fun parse(html: String, p: Pattern): String {
        val m = p.matcher(html)
        if (m.find()) {
            return html.substring(m.start(), m.end())
        }
        return ""
    }

    fun parse(html: String, p: String) = parse(html, Pattern.compile(p))

    fun parseBlock(html: String, tag: String) = parse(html, "<$tag[\\W]*[^>]*>.*</$tag>")

    fun parseTag(html: String, tag: String)= parse(html, "<$tag[\\W]*[^>]*>")

    fun parseParam(html: String, param: String): String? {
        val p = Pattern.compile("$param=\"([^\"]*)\"")
        val m = p.matcher(html.replace("\n", ""))
        if (m.find()) {
            return m.group(1) ?: ""
        }
        return ""
    }

    fun parseUrlParam(url: String, param: String) = parse(url, "$param=[^&]*").split("=").last()

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

    // https://oauth.vk.com/authorize?client_id=6708705&redirect_uri=https://oauth.vk.com/blank.html&display=page&scope=friends&response_type=token&v=5.85
    // https://oauth.vk.com/blank.html#access_token=569ecb9d54e934172b828eecee654736a62ac9995bbc642697362475153c753128164af7208ca80979db0&expires_in=86400&user_id=508731237

    fun readToken() {
        var url = "https://oauth.vk.com/authorize?client_id=$APP_ID&redirect_uri=$REDIRECT_URI&display=page&scope=wall,offline&response_type=token&v=5.85"
        var response = get(url)
        val data = getLoginData(response.text, email, pass)
        var cookies = response.cookies.toMap()
        response = post("https://login.vk.com?act=login&soft=1", cookies = cookies, data = data)
        cookies = response.cookies.toMap()
        val tag = parseTag(response.text, "form")
        val action = parseParam(tag, "action")!!
        println(action)
        response = get(action, cookies=cookies)
//        url = "https://oauth.vk.com/blank.html#access_token=9cb221abef689accae154c0c1cd6ca9202f0d5298d81f0ffe601f76ea8692f863989cd56a8d1abe9d8810&expires_in=86400&user_id=508731237"
        url = response.url
        accessToken = parseUrlParam(url, "access_token")
        val userId = parseUrlParam(url, "user_id")
        println("accessToken: $accessToken")
        println("userId: $userId")
        println("expires_in: ${parseUrlParam(url, "expires_in")}")
    }

    private fun request(methodName: String, p: Map<String, String>) {
        val params = p.toMutableMap()
        params["access_token"] = accessToken
        params["v"] = "5.85"
        val response = get("https://api.vk.com/method/$methodName", params = params)
        println("$methodName, $params -> ${response.statusCode}")
    }

    fun wallPost(msg: String, friendsOnly: Boolean) = request("wall.post", mapOf(
            "owner_id" to "$userId",
            "friends_only" to if (friendsOnly) "1" else "0",
            "message" to msg,
            "guid" to "${Random().nextLong()}"
    ))
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
//    val spider = VKSpider("89266552375", "m31k0l2")
    val spider = VKSpider("89166562389", "Love1987")
    spider.readToken()
//    spider.accessToken = "9caf91c20a0481e0dba3747169d01da13837b54bbb2a7d4afad60a40625dffd723ff95ae35a4d993a7d38"
//    spider.userId = 508731237
    spider.wallPost("...", true)
}