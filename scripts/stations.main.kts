#!/usr/bin/env kotlin

@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
@file:DependsOn("org.jsoup:jsoup:1.21.2")
@file:DependsOn("org.apache.commons:commons-csv:1.14.1")

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.commons.io.output.TeeOutputStream
import org.jsoup.Jsoup
import java.io.File


fun parseDMS(dms: String): Float {
    val trimmed = dms.trim()
    val sign = when {
        trimmed.startsWith("북위") || trimmed.startsWith("동경") -> +1
        trimmed.startsWith("남위") || trimmed.startsWith("서경") -> -1
        else -> throw IllegalArgumentException()
    }

    val regex = """(\d+)°\s*(\d+)′\s*([\d.]+)″""".toRegex()
    val match = regex.find(trimmed) ?: throw IllegalArgumentException("Invalid DMS format")
    val (deg, min, sec) = match.destructured

    return sign * (deg.toFloat() + min.toFloat() / 60 + sec.toFloat() / 3600)
}


val document = Jsoup.connect("https://namu.wiki/w/한국철도공사/역명코드").get()

val data = document.select("table.Agu2vgbF")[1]
    .select("tr")
    .mapNotNull { row ->
        val cells = row.select("td")

        if (
            cells[0].selectFirst("del") != null ||
            "†" in cells[4].text()
        ) return@mapNotNull null

        cells.mapIndexed { index, cell ->
            if (index == 0 && cell.selectFirst("del") != null) {
                return@mapNotNull null
            }

            when (index) {
                1, 2 ->  // 한글, 한자에서 공백 제거
                    cell.text().filterNot { it.isWhitespace() }

                else -> cell.text()
            }
        }
    }


val conversion = mapOf(
    "가야" to "가야역_(한국철도공사)",
    "강구" to "강구역_(영덕)",
    "고양기지" to "행신역",
    "구룡" to "구룡역_(순천)",
    "구모량" to "모량역",
    "구부조" to "부조역",
    "구포" to "구포역_(한국철도공사)",
    "금곡" to "금곡역_(남양주)",
    "금호" to "금호역_(영천)",
    "기성" to "기성역_(울진)",
    "남포" to "남포역_(보령)",
    "내포" to "내포역_(예산)",
    "녹동" to "녹동역_(봉화)",
    "대곡" to "대곡역_(고양)",
    "대공원" to "대공원역_(과천)",
    "도안" to "도안역_(증평)",
    "동래" to "동래역_(한국철도공사)",
    "백원" to "백원역_(상주)",
    "범일" to "범일역_(한국철도공사)",
    "봉화" to "봉화역_(봉화)",
    "부산진" to "부산진역_(한국철도공사)",
    "부전" to "부전역_(한국철도공사)",
    "사상" to "사상역_(한국철도공사)",
    "삼산(중앙선)" to "삼산역",
    "상동" to "상동역_(밀양)",
    "성산" to "성산역_(순천)",
    "송정" to "송정역_(부산)",
    "수서(고속선)" to "수서역",
    "수서(분당선)" to "수서역",
    "순천" to "순천역_(전라남도)",
    "신기" to "신기역_(삼척)",
    "신원" to "신원역_(양평)",
    "신진영" to "진영역",
    "신촌" to "신촌역_(경의선)",
    "쌍룡" to "쌍룡역_(영월)",
    "안평" to "안평역_(장성)",
    "양원" to "양원역_(봉화)",
    "양평" to "양평역_(양평)",
    "연산" to "연산역_(논산)",
    "연풍" to "연풍역_(괴산)",
    "용문" to "용문역_(양평)",
    "운천" to "운천역_(파주)",
    "일신" to "일신역_(양평)",
    "장흥" to "장흥역_(양주)",
    "제천순환" to "제천역",
    "좌천" to "좌천역_(한국철도공사)",
    "중동" to "중동역_(부천)",
    "중앙" to "중앙역_(안산)",
    "진부(오대산)" to "진부역",
    "판교" to "판교역_(서천)",
    "판교(경기)" to "판교역_(성남)",
    "판교(충남)" to "판교역_(서천)",
    "화명" to "화명역_(한국철도공사)",
    "화정" to "화정역_(고양)",
    "효자" to "효자역_(포항)",
)

fun getLatLng(stationName: String): List<Float>? {
    val pageName = conversion.getOrDefault(stationName, "${stationName}역")
    return runCatching {
        Jsoup.connect("https://ko.wikipedia.org/wiki/$pageName").get()
            .selectFirst(".geo-dms")!!.run {
                val latitude = parseDMS(selectFirst(".latitude")!!.text())
                val longitude = parseDMS(selectFirst(".longitude")!!.text())

                listOf(latitude, longitude)
            }
    }.getOrNull()
}



TeeOutputStream(
    CloseShieldOutputStream.wrap(System.out),
    File("../data/processed/stations.csv").outputStream()
).use { tee ->
    CSVPrinter(tee.writer(), CSVFormat.DEFAULT).use { printer ->
        printer.printRecord(data[0] + "좌표")

        data.drop(1).forEach {
            printer.printRecord(it + getLatLng(it[1]))
        }
    }
}



