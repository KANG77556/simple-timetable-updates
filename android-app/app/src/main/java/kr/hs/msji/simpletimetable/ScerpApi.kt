package kr.hs.msji.simpletimetable

import org.json.JSONArray
import org.json.JSONObject
import java.net.CookieHandler
import java.net.CookieManager
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ScerpApi {
    companion object {
        const val BASE_URL = "https://scerp.cloud"
        init { CookieHandler.setDefault(CookieManager()) }
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "SimpleTimetable-Android/0.1.0")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (text.isBlank()) throw IllegalStateException("SCERP 응답이 비어 있습니다. HTTP $code")
        val json = JSONObject(text)
        if (!json.optBoolean("ok", code in 200..299)) {
            val message = json.optJSONObject("error")?.optString("message")
                ?: json.optString("message")
                ?: "SCERP 요청 실패"
            throw IllegalStateException(message)
        }
        return json
    }

    fun login(loginId: String, password: String): UserProfile {
        request("/api/auth/login", "POST", JSONObject().put("loginId", loginId).put("password", password))
        val me = request("/api/me").optJSONObject("data") ?: JSONObject()
        return UserProfile(
            userId = me.optString("userId"),
            displayName = me.optString("displayName", me.optString("loginId")),
            schoolName = me.optString("schoolName", "밀성제일고등학교")
        )
    }

    fun fetchMyTimetable(date: String, userId: String): List<TimetableItem> {
        val q = "?from=${URLEncoder.encode(date, "UTF-8")}&to=${URLEncoder.encode(date, "UTF-8")}"
        val arr = request("/api/timetable$q").optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val rowUserId = row.optString("teacher_user_id", row.optString("teacherUserId"))
                if (rowUserId != userId) continue
                val period = row.optInt("period", 0)
                if (period !in 1..7) continue
                add(TimetableItem(
                    date = date,
                    grade = row.optInt("grade", 0),
                    classCode = row.optString("class_code", row.optString("classCode")),
                    period = period,
                    subject = row.optString("subject"),
                    room = row.optString("room"),
                    startTime = row.optString("start_time", row.optString("startTime")),
                    endTime = row.optString("end_time", row.optString("endTime"))
                ))
            }
        }.sortedBy { it.period }
    }

    fun fetchPublicTimetable(date: String): List<TimetableItem> {
        val q = "?from=${URLEncoder.encode(date, "UTF-8")}&to=${URLEncoder.encode(date, "UTF-8")}"
        val arr = request("/api/public/timetable$q").optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val period = row.optInt("period", 0)
                val grade = row.optInt("grade", 0)
                val classCode = row.optString("class_code", row.optString("classCode"))
                if (period !in 1..7 || grade <= 0 || classCode.isBlank()) continue
                add(TimetableItem(
                    date = row.optString("date", date),
                    grade = grade,
                    classCode = classCode,
                    period = period,
                    subject = row.optString("subject"),
                    teacher = row.optString("teacher_alias", row.optString("teacherAlias")),
                    room = row.optString("room"),
                    startTime = row.optString("start_time", row.optString("startTime")),
                    endTime = row.optString("end_time", row.optString("endTime"))
                ))
            }
        }
    }

    fun fetchClassrooms(): List<Classroom> {
        val arr = request("/api/eboard-center/classrooms").optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val id = row.optString("id")
                if (id.isBlank()) continue
                add(Classroom(id = id, name = row.optString("name", id), onlineCount = row.optInt("online_count", 0)))
            }
        }.sortedBy { it.name }
    }

    fun sendBroadcast(classroomIds: List<String>, text: String, tts: Boolean): String {
        require(classroomIds.isNotEmpty()) { "방송할 학급을 선택해 주세요." }
        require(text.trim().isNotEmpty()) { "방송 내용을 입력해 주세요." }
        val payload = JSONObject()
            .put("classroomIds", JSONArray(classroomIds))
            .put("text", text.trim())
            .put("priority", "NORMAL")
            .put("display", true)
            .put("tts", tts)
            .put("repeat", 1)
            .put("speechRate", 1)
            .put("pitch", 1)
            .put("displaySeconds", 10)
        val data = request("/api/eboard-center/send", "POST", payload).optJSONObject("data")
        return data?.optString("messageId", data.optString("broadcastId")) ?: "전송 완료"
    }
}
