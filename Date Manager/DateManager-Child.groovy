/**
 *  ****************  Date Manager Child ****************
 *
 *  Design Usage:
 *  Countdown to important dates/holidays
 *
 *  Originally Copyright 2019-2020 Bryan Turcotte (@bptworld)
 *  Copyright 2020 Carl Kaehler (@compgeek)
 *
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  If modifying this project, please keep the above header intact and add your comments/credits below - Thank you! -  @Compgeek
 *
 * ------------------------------------------------------------------------------------------------------------------------------
 *
 *  Changes:
 *
 *  V1.0.0 - 7/7/2020 - Initial Create / Modify
 *
 */

import groovy.time.TimeCategory

def setVersion(){
	if(logEnable) log.debug "In setVersion"
    state.appName = "DateManagerChild"
	state.version = "v1.0.0"
}

definition(
    name: "Date Manager Child",
    namespace: "Compgeek",
    author: "Carl Kaehler",
    description: "Countdown to important dates/holidays",
    category: "",
    parent: "Compgeek:Date Manager",
    iconUrl: "",
    iconX2Url: "",
    iconX3Url: ""
)

preferences {
    page name: "pageMain"
}

def pageMain() {
    dynamicPage(name: "", title: "", install: true, uninstall: true) {
		display() 
        section("${getImage('instructions')} <b>Instructions:</b>", hideable: true, hidden: true) {
            paragraph "<b>Notes:</b>"
            paragraph "Choose your URL, Device and Options."
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Data Options")) {
			input "uri", "string", title: "JSON URL:", required: true, displayDuringSetup: true 
			input "holidaySwitch", "capability.switch", title: "Holiday Switch:", required: false, displayDuringSetup: true
			input "observanceSwitch", "capability.switch", title: "Observance Switch:", required: false, displayDuringSetup: true
			input "otherSwitch", "capability.switch", title: "Other Notable Day Switch:", required: false, displayDuringSetup: true
			input "birthdaySwitch", "capability.switch", title: "Birthday Switch:", required: false, displayDuringSetup: true
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Text Color Options")) {
            paragraph "When date is getting close, the text color can be changed so it stands out.<br>ie. Black, Blue, Brown, Green, Orange, Red, Yellow, White, etc."
			input "sevenDayColor", "text", title: "Seven Days Out", required: true, defaultValue: "Green", width:4
            input "threeDayColor", "text", title: "Three Days Out", required: true, defaultValue: "Orange", width:4
            input "theDayColor", "text", title: "The Days Of", required: true, defaultValue: "Red", width:4
		}
		section(getFormat("header-blue", "${getImage("Blank")}"+" When to Run")) {
			input "timeToRun", "time", title: "Check daily at", required: true
			input "forceRun", "capability.switch", title: "Run on Demand Switch:", required: false, displayDuringSetup: true 
		}
		section(getFormat("header-blue", "${getImage("Blank")}"+" Dashboard Tile")) {}
		section("Instructions for Dashboard Tile:", hideable: true, hidden: true) {
			paragraph "<b>Want to be able to view your data on a Dashboard? Now you can, simply follow these instructions!</b>"
			paragraph " - Create a new 'Virtual Device' using our 'Date Manager Driver'.<br> - Then select this new device below.<br> - Now all you have to do is add this device to any of your dashboards to see your data on a tile!"
            paragraph "- Example: I have 3 child apps/virtual devices for dates...<br> - Date Manager - Holidays<br> - Date Manager - Special<br> - Date Manager - School Days Off"
		}
		section() {
			input "tileDevice", "capability.actuator", title: "Vitual Device created to send the data to:", submitOnChange: true, required: false, multiple: false
        }
		section(getFormat("header-blue", "${getImage("Blank")}"+" General")) {label title: "Enter a name for this automation", required: false}
        section() {
            input "logEnable", "bool", defaultValue: false, title: "Enable Debug Logging", description: "debugging"
		}        
		display2()
    }
}

def installed() {
    if(logEnable) log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {	
    if(logEnable) log.debug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
	initialize()
}

def initialize() {
    if(logEnable) log.debug "Initializing"
	setVersion()
	setDefaults()
	if (forceRun) subscribe(forceRun, "switch", forceHandler)
	schedule(timeToRun, getJson)
}

def forceHandler(evt) {
	switch(evt.value)
	{
		case "on":
			if(logEnable) log.debug "Force run Via Switch"
			getJson()
			break;
	}
}

def getJson() {
	def ParamsJson = [ uri: uri ]
	if(logEnable) log.debug "Poll Date Manager Child: " + ParamsJson
	asynchttpGet('pollJsonHandler', ParamsJson)
	return
}

def pollJsonHandler(resp, data) {
	if(resp.getStatus() == 200 || resp.getStatus() == 207) {
		def jsonData = parseJson(resp.data)
		if(logEnable) log.debug "API Data: " + jsonData
		doProcessJson(jsonData)
		turnDevicesOff()
		doProcessMap()
	} else {
		log.warn 'Date Manager Child WARNING: Calling ' + uri
		log.warn 'Date Manager Child WARNING: API did not return data. ' + resp.getStatus() + ':' + resp.getErrorMessage()
	}
	return
}

def doProcessJson(ArrayList jsonData) {
    if(logEnable) log.debug "In doProcessJson"
	Calendar calendar = new GregorianCalendar()
    int currentYear = calendar.get(Calendar.YEAR)
    def nowDate = new Date().format('MM/dd/yyyy', location.timeZone)        
	Date todayDate = Date.parse('MM/dd/yyyy', nowDate).clearTime()
	Date eventDate
	def eventName
	state.reminderMap = [:]

    jsonData.each { it ->
        //if(logEnable) log.debug it
		it.name.each { na ->
			if (na.lang == "en") {
				eventName = na.text
				exit
			}
		}
		eventDate = Date.parse('MM/dd/yyyy', "${it.date.month}/${it.date.day}/${it.date.year}").clearTime()
		state.reminderMap.put(eventDate, [name: eventName, type: it.holidayType])
		if(logEnable) log.debug eventDate
		if(logEnable) log.debug eventName
    }
	if(logEnable) log.debug state.reminderMap
    return
}

def turnDevicesOff() {
	if(logEnable) log.debug "In turnDevicesOff (${state.version})"
	if(holidaySwitch) holidaySwitch.off()
	if(observanceSwitch) observanceSwitch.off()
	if(otherSwitch) otherSwitch.off()
	if(birthdaySwitch) birthdaySwitch.off()
	return
}

def doProcessMap() {
	if(logEnable) log.debug "In doProcessMap (${state.version})"
	def nowDate = new Date().format('MM/dd/yyyy', location.timeZone)
	Date todayDate = Date.parse('MM/dd/yyyy', nowDate).clearTime()
	//def sortedMap = state.reminderMap.sort { a, b -> a.key <=> b.key }
	def sortedMap = state.reminderMap
	def reminderString = "<table width='100%'>"
	reminderString += "<tr><td width='8%'><b>Date</b></td><td width='2%'> </td><td width='80%'><b>Reminder</b></td><td width='10%'><b>Days</b></td></tr>"
	reminderString2 = reminderString
	if(sortedMap) {
		if(logEnable) log.debug "Processing Map."
		def count = 0
		def reminderCount = 0
		sortedMap.each { it ->
            if(logEnable) log.debug "Processing Item:" + it.value.name
			def eventDate = it.key
			def eventName = it.value.name
			def eventType = it.value.type

			count += 1
			reminderCount += 1

			def daysLeft = TimeCategory.minus(eventDate, todayDate)
            if(logEnable) log.debug "Days Left:" + daysLeft
			if(daysLeft.days >= 0) {
				if(logEnable) log.debug "In doProcessMap - count: ${count} - eventName: ${eventName} - daysLeft: ${daysLeft.days} - eventType: ${eventType}"
				if(daysLeft.days >= 8) formattedDaysLeft = "${daysLeft.days}"
                if(daysLeft.days <= 7 && daysLeft.days >= 4) formattedDaysLeft = "<div style='color: ${sevenDayColor};'><b>${daysLeft.days}</b></div>"
				if(daysLeft.days <= 3 && daysLeft.days >= 1) formattedDaysLeft = "<div style='color: ${threeDayColor};'><b>${daysLeft.days}</b></div>"
				if(daysLeft.days == 0) {
					formattedDaysLeft = "<div style='color: ${theDayColor};'><b>Today!</b></div>"
					switch(eventType)
					{
						case "public_holiday":
							if(holidaySwitch) holidaySwitch.on()
							break;
						case "observance":
							if(observanceSwitch) observanceSwitch.on()
							break;
						case "other_day":
							if(otherSwitch) otherSwitch.on()
							break;
						case "birthday":
							if(birthdaySwitch) birthdaySwitch.on()
							break;
					}
				}
				if((count >= 1) && (count <= 5)) {
					reminderString += "<tr><td width='8%'>${eventDate.getDateString()}</td><td width='2%'> </td><td width='80%'>${eventName}</td><td width='10%'>${formattedDaysLeft}</td></tr>"
				} else if((count >= 6) && (count <= 10)) {
					reminderString2 += "<tr><td width='8%'>${eventDate.getDateString()}</td><td width='2%'> </td><td width='80%'>${eventName}</td><td width='10%'>${formattedDaysLeft}</td></tr>"
				} else {
					exit
				}
			}
		} 
	} else {
		if(state.reminderMap == null) reminderString = " Nothing to display"
	}
	reminderString += "</table>"
	reminderString2 += "</table>"
	if(logEnable) log.debug "${reminderString}"
	if(logEnable) log.debug "${reminderString2}"

	if(tileDevice) {
    	if(logEnable) log.debug "Sending maps to ${tileDevice}"
	    tileDevice.setReminder1(reminderString)
	    tileDevice.setReminder2(reminderString2)
    }
}

// ********** Normal Stuff **********

def setDefaults(){
	if(logEnable == null){logEnable = false}
	if(state.msg == null){state.msg = ""}
}

def getImage(type) {					// Modified from @Stephack Code
    def loc = "<img src=https://raw.githubusercontent.com/bptworld/Hubitat/master/resources/images/"
    if(type == "Blank") return "${loc}blank.png height=40 width=5}>"
    if(type == "checkMarkGreen") return "${loc}checkMarkGreen2.png height=30 width=30>"
    if(type == "optionsGreen") return "${loc}options-green.png height=30 width=30>"
    if(type == "optionsRed") return "${loc}options-red.png height=30 width=30>"
    if(type == "instructions") return "${loc}instructions.png height=30 width=30>"
    if(type == "logo") return "${loc}logo.png height=60>"
}

def getFormat(type, myText=""){			// Modified from @Stephack Code   
	if(type == "header-blue") return "<div style='color:#ffffff;font-weight: bold;background-color:#95CAFF;border: 1px solid;box-shadow: 2px 3px #A9A9A9'>${myText}</div>"
    if(type == "line") return "<hr style='background-color:#1A77C9; height: 1px; border: 0;'>"
    if(type == "title") return "<h2 style='color:#1A77C9;font-weight: bold'>${myText}</h2>"
}

def display() {
    theName = app.label
    if(theName == null || theName == "") theName = "New Child App"
    section (getFormat("title", "${getImage("logo")}" + " Date Manager - ${theName}")) {
		paragraph getFormat("line")
	}
}

def display2(){
	setVersion()
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Date Manager - @Compgeek<br><a href='https://bitbucket.org/ckaehler/hubitat/src/master/' target='_blank'>Find more apps on my Bitbucket, just click here!</a><br>${state.version}</div>"
	}       
}