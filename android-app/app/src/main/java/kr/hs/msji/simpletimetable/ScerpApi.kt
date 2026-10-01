package kr.hs.msji.simpletimetable

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class ScerpApiException(
    val status: Int,
    val code: String,
    message: String
) : IllegalStateException(message)

class ScerpApi(private val store: LocalStore? = null) {
    companion object {
        const val BASE_URL = "https://scerp.cloud"
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null): JSONObject {
        val connection = URL(BASE_URL + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Origin", BASE_URL)
        connection.setRequestProperty("User-Agent", "SimpleTimetable-Android/${BuildConfig.VERSION_NAME}")
        store?.sessionCookie?.takeIf { it.isNotBlank() }?.let {
            connection.setRequestProperty("Cookie", it)
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val setCookies = connection.headerFields.entries
            .firstOrNull { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
            ?.value
            .orEmpty()
            .mapNotNull { it.substringBefore(';').trim().takeIf(String::isNotBlank) }
        if (setCookies.isNotEmpty()) {
            store?.sessionCookie = setCookies.joinToString("; ")
        }
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        if (text.isBlank()) {
            throw ScerpApiException(code, "empty_response", "SCERP 응답이 비어 있습니다. HTTP $code")
        }
        val json = JSONObject(text)
        if (!json.optBoolean("ok", code in 200..299)) {
            val error = json.optJSONObject("error")
            val errorCode = error?.optString("code").orEmpty().ifBlank { "request_failed" }
            val message = error?.optString("message")
                ?: json.optString("message")
                ?: "SCERP 요청 실패"
            throw ScerpApiException(code, errorCode, message)
        }
        return json
    }

    fun login(loginId: String, password: String): UserProfile {
        request("/api/auth/login", "POST", JSONObject().put("loginId", loginId).put("password", password))
        val me = request("/api/me").optJSONObject("data") ?: JSONObject()
        return UserProfile(
            userId = firstNonBlank(me, "userId", "user_id", "id"),
            displayName = firstNonBlank(me, "displayName", "display_name", "name", "loginId", "login_id"),
            schoolName = firstNonBlank(me, "schoolName", "school_name").ifBlank { "밀성제일고등학교" }
        )
    }

    private fun normalizeTeacherName(value: String): String =
        value
            .trim()
            .replace("선생님", "")
            .replace("교사", "")
            .replace("담당", "")
            .replace(Regex("[\\s·._-]+"), "")
            .lowercase()

    private fun teacherMatches(displayName: String, teacherAlias: String): Boolean {
        val profile = normalizeTeacherName(displayName)
        val alias = normalizeTeacherName(teacherAlias)
        if (profile.isBlank() || alias.isBlank()) return false
        if (profile == alias) return true
        val shorter = minOf(profile.length, alias.length)
        return shorter >= 2 && (profile.startsWith(alias) || alias.startsWith(profile))
    }

    private fun firstNonBlank(row: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = row.optString(key).trim()
            if (value.isNotBlank() && value != "null") return value
        }
        return ""
    }

    private fun teacherUserId(row: JSONObject): String =
        firstNonBlank(
            row,
            "teacher_user_id",
            "teacherUserId",
            "user_id",
            "userId",
            "teacher_id",
            "teacherId",
            "teacher_user",
            "teacherUser"
        )

    private fun teacherName(row: JSONObject): String =
        firstNonBlank(
            row,
            "teacher_alias",
            "teacherAlias",
            "teacher_name",
            "teacherName",
            "teacher",
            "teacher_display_name",
            "teacherDisplayName",
            "staff_name",
            "staffName"
        )

    private fun rowMatchesTeacher(row: JSONObject, userId: String, displayName: String): Boolean {
        val rowUserId = teacherUserId(row)
        if (rowUserId.isNotBlank() && userId.isNotBlank() && rowUserId == userId) return true
        return teacherMatches(displayName, teacherName(row))
    }

    private fun rowToTimetable(row: JSONObject, fallbackDate: String): TimetableItem? {
        val period = row.optInt("period", 0)
        if (period !in 1..7) return null
        return TimetableItem(
            date = firstNonBlank(row, "date", "lesson_date", "lessonDate").ifBlank { fallbackDate },
            grade = row.optInt("grade", row.optInt("grade_no", 0)),
            classCode = firstNonBlank(row, "class_code", "classCode", "class_name", "className", "class"),
            period = period,
            subject = firstNonBlank(row, "subject", "subject_name", "subjectName", "course_name", "courseName"),
            teacher = teacherName(row),
            room = firstNonBlank(row, "room", "classroom", "room_name", "roomName"),
            startTime = firstNonBlank(row, "start_time", "startTime"),
            endTime = firstNonBlank(row, "end_time", "endTime")
        )
    }

    private fun distinctTimetable(rows: List<TimetableItem>): List<TimetableItem> =
        rows.distinctBy {
            listOf(it.date, it.period.toString(), it.grade.toString(), it.classCode, it.subject, it.room).joinToString("|")
        }.sortedBy { it.period }

    fun fetchMyTimetable(date: String, userId: String, displayName: String = ""): List<TimetableItem> {
        val q = "?from=${URLEncoder.encode(date, "UTF-8")}&to=${URLEncoder.encode(date, "UTF-8")}"
        val primary = request("/api/timetable$q").optJSONArray("data") ?: JSONArray()

        val primaryMatches = buildList {
            for (i in 0 until primary.length()) {
                val row = primary.optJSONObject(i) ?: continue
                if (!rowMatchesTeacher(row, userId, displayName)) continue
                rowToTimetable(row, date)?.let(::add)
            }
        }
        if (primaryMatches.isNotEmpty()) return distinctTimetable(primaryMatches)

        // 일부 SCERP 시간표 응답은 개인 식별 필드를 생략할 수 있으므로,
        // 전체 학급 시간표에서 교사명으로 한 번 더 조회한다.
        val publicRows = request("/api/public/timetable$q").optJSONArray("data") ?: JSONArray()
        val fallbackMatches = buildList {
            for (i in 0 until publicRows.length()) {
                val row = publicRows.optJSONObject(i) ?: continue
                if (!teacherMatches(displayName, teacherName(row))) continue
                rowToTimetable(row, date)?.let(::add)
            }
        }
        return distinctTimetable(fallbackMatches)
    }

    fun fetchPublicTimetable(date: String): List<TimetableItem> {
        val q = "?from=${URLEncoder.encode(date, "UTF-8")}&to=${URLEncoder.encode(date, "UTF-8")}"
        val arr = request("/api/public/timetable$q").optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until arr.length()) {
                val row = arr.optJSONObject(i) ?: continue
                val item = rowToTimetable(row, date) ?: continue
                if (item.grade <= 0 || item.classCode.isBlank()) continue
                add(item)
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

    fun fetchNotePages(): List<NotePage> {
        val data = request("/api/notes").optJSONObject("data") ?: JSONObject()
        val pages = data.optJSONArray("pages") ?: JSONArray()
        return (0 until pages.length()).mapNotNull { i ->
            val p = pages.optJSONObject(i) ?: return@mapNotNull null
            NotePage(
                id = p.optString("id"),
                title = p.optString("title"),
                category = p.optString("category", "개인"),
                tags = p.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { j -> arr.optString(j) } } ?: emptyList(),
                pinned = p.optBoolean("pinned"),
                archived = p.optBoolean("archived"),
                version = p.optLong("version", 1),
                createdAt = p.optString("createdAt"),
                updatedAt = p.optString("updatedAt"),
                syncState = "SYNCED"
            )
        }
    }

    fun fetchNote(id: String): NotePage {
        val data = request("/api/notes?id=${URLEncoder.encode(id, "UTF-8")}").optJSONObject("data") ?: JSONObject()
        val p = data.optJSONObject("page") ?: throw IllegalStateException("메모를 찾을 수 없습니다.")
        val blocks = data.optJSONArray("blocks") ?: JSONArray()
        return NotePage(
            id = p.optString("id"),
            title = p.optString("title"),
            category = p.optString("category", "개인"),
            tags = p.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { j -> arr.optString(j) } } ?: emptyList(),
            pinned = p.optBoolean("pinned"),
            archived = p.optBoolean("archived"),
            version = p.optLong("version", 1),
            createdAt = p.optString("createdAt"),
            updatedAt = p.optString("updatedAt"),
            blocks = (0 until blocks.length()).mapNotNull { i ->
                val b = blocks.optJSONObject(i) ?: return@mapNotNull null
                NoteBlock(
                    id = b.optString("id"),
                    type = NoteBlockType.fromWire(b.optString("type")),
                    content = b.optString("content"),
                    checked = b.optBoolean("checked"),
                    position = b.optInt("position", i)
                )
            },
            syncState = "SYNCED"
        )
    }

    fun saveNote(page: NotePage): NotePage {
        val blocks = JSONArray()
        page.blocks.sortedBy { it.position }.forEach { b ->
            blocks.put(JSONObject()
                .put("id", b.id)
                .put("type", b.type.wireName)
                .put("content", b.content)
                .put("checked", b.checked)
                .put("position", b.position))
        }
        val payload = JSONObject()
            .put("id", page.id)
            .put("title", page.title)
            .put("category", page.category)
            .put("tags", JSONArray(page.tags))
            .put("pinned", page.pinned)
            .put("archived", page.archived)
            .put("version", if (page.version <= 0) JSONObject.NULL else page.version)
            .put("blocks", blocks)
        val data = request("/api/notes", "PUT", payload).optJSONObject("data") ?: JSONObject()
        return page.copy(
            id = data.optString("id", page.id),
            version = data.optLong("version", page.version.coerceAtLeast(1)),
            updatedAt = data.optString("updatedAt", page.updatedAt),
            syncState = "SYNCED"
        )
    }

    fun archiveNote(id: String) {
        request("/api/notes?id=${URLEncoder.encode(id, "UTF-8")}", "DELETE")
    }
}
