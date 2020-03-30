package com.tencent.bkrepo.helm

import com.tencent.bkrepo.helm.constants.INIT_STR
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*


class TimeTest {
	//@Throws(ParseException::class)
	fun dealDateFormat(oldDateStr: String): String {
		//此格式只有  jdk 1.7才支持  yyyy-MM-dd'T'HH:mm:ss.SSSXXX
		//yyyy-MM-dd'T'HH:mm:ss.SSS
//		val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")  //yyyy-MM-dd'T'HH:mm:ss.SSSZ
//		val date = df.parse(oldDateStr)
//		val df1 = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.UK)
//		val date1 = df1.parse(date.toString())
//		val df2 = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
//		//  Date date3 =  df2.parse(date1.toString());
//		return df2.format(date1)

		val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
//		val date = LocalDateTime.parse(node.nodeInfo.lastModifiedDate, format)
		return LocalDateTime.now().format(format)
	}

	@Test
	fun test() {
//		dealDateFormat("2020-03-25T17:35:10+08:00")
//		print(dealDateFormat("2020-03-25T10:53:21.594Z"))
//		val date = LocalDateTime.parse("2017-02-03T12:30:30")

//		val date = LocalDateTime.now()
//		println(date)
//		val date1 = date.atZone(ZoneId.systemDefault())
//		println(date1.toLocalDateTime())

//		val zone = ZoneId.systemDefault()
//		val localDateTime = LocalDateTime.now()
////Instant是一个精确到纳秒的时间对象
//		val instant = localDateTime.atZone(zone).toInstant()

//		val zonedDateTime = ZonedDateTime.now();
//		System.out.println(zonedDateTime);
//		// 获得时区
//		val zone = zonedDateTime.getZone();
//		System.out.println("\n"+zone);
//		// 获得指定时区时间
//		val zonedDateTime2 = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"));
//		System.out.println("\n"+zonedDateTime2);

//		val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00")
//		val date = LocalDateTime.now().format(format)
//		print(date)

//		println(ofInstant)

//		val format = LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS"))
//		System.err.println(format)
		val of = ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault())
		val format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS+08:00").format(of)
		System.err.println(format1)
		System.err.println(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS+08:00")))

		val format2 = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss+08:00")
		val of1 = ZonedDateTime.of(LocalDateTime.now(), ZoneId.systemDefault())
		val initStr = String.format(INIT_STR, format2.format(of1))
		System.err.println(initStr)

		System.err.println(String.format(INIT_STR, LocalDateTime.now().format(format2)))

	}
}