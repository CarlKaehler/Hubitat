/**
 *  ****************  Commute Time Child ****************
 *
 *  Design Usage:
 *  Have a color bulb notify of commute issues
 *
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
 *  V1.0.0 - 10/04/19 - Initial release.
 *
 */

def setVersion(){
	if(logEnable) log.debug "In setVersion"
    state.appName = "CommuteTimeChild"
	state.version = "v1.0.0"
}

definition(
    name: "Commute Time Child",
    namespace: "Compgeek",
    author: "Carl Kaehler",
    description: "Use Color Bulbs to Notify of Commute Time",
    category: "",
    parent: "Compgeek:Commute Time",
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
            paragraph "Choose your start and end destinations as well as your light."
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Addresses")) {
            input "startAddress", "text", title: "Starting Address", required: true, displayDuringSetup: true
            input "destinationAddress", "text", title: "Destination Address", required: true, displayDuringSetup: true
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Triggers")) {
            input "switchDevice", "capability.switch", title: "Switch Trigger", required: false, multiple: false, submitOnChange: true
            input "runEvery", "number", title: "Run every (x) Minutes", required: true, displayDurningSetup: true
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Lights")) {
            input "controlLights", "capability.colorControl", title: "Select Lights", multiple: true, required: true
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" General")) {label title: "Enter a name for this child app", required: false, submitOnChange: true}
        section() {
			input(name: "logEnable", type: "bool", defaultValue: "false", submitOnChange: "true", title: "Enable Debug Logging", description: "debugging")
    	}
        display2()
    }
}

// ********** Normal Stuff **********

def setDefaults(){
	if(logEnable == null){logEnable = false}
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

def installed() {
    log.debug "Installed with settings: ${settings}"
	initialize()
}

def updated() {	
    if(logEnable) log.debug "Updated with settings: ${settings}"
	unschedule()
    unsubscribe()
	initialize()
}

def initialize() {
	if(switchDevice) subscribe(switchDevice, "switch.on", deviceStateHandler)
}

def deviceStateHandler(evt) {
    if(logEnable) log.debug "In deviceStateHandler (${state.version})"
    checkCommute()
}

def checkCommute() {
    if(logEnable) log.debug "In checkCommute (${state.version})"
    if(switchDevice.currentValue('switch') == "on") {
        try {
            def apiUrl = "https://maps.googleapis.com/maps/api/distancematrix/json?departure_time=now&origins=${URLEncoder.encode(startAddress, "UTF-8")}&destinations=${URLEncoder.encode(destinationAddress, "UTF-8")}&key=${parent.apiKey}"
            if(logEnable) log.debug "API Url: ${apiUrl}"
            httpGet(apiUrl) { resp ->
                if (resp.success) {
                    if(logEnable) log.debug "Result Success"
                    def minutesInTraffic = resp.data.rows[0].elements[0].duration_in_traffic.value / 60
                    def typicalMinutes = resp.data.rows[0].elements[0].duration.value / 60
                    def delayTrigger = typicalMinutes / 6
                    def newColorValue
                    if (minutesInTraffic <= typicalMinutes + delayTrigger) {
                        newColorValue = [hue: 39, saturation: 100, level: 40]
                    } else if (minutesInTraffic > typicalMinutes + delayTrigger && minutesInTraffic < typicalMinutes + (delayTrigger * 2)) {
                        newColorValue = [hue: 10, saturation: 100, level: 40]
                    } else if (minutesInTraffic >= typicalMinutes + (delayTrigger * 2)) {
                        newColorValue = [hue: 100, saturation: 100, level: 40]
                    }
                    controlLights.each { li ->
                        li.setColor(newColorValue)
                    }
                    
                }
                if (logEnable)
                    if (resp.data) log.debug "${resp.data}"                    
            }
        } catch (Exception e) {
            log.warn "Call to on failed: ${e.message}"
        }
        runIn(60 * runEvery, checkCommute)
    } else {
        controlLights.each { li ->
            li.off()
        }
    }
}

def display() {
    theName = app.label
    if(theName == null || theName == "") theName = "New Child App"
    section (getFormat("title", "${getImage("logo")}" + " Commute Time - ${theName}")) {
		paragraph getFormat("line")
	}
}

def display2(){
	setVersion()
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Commute Time - @Compgeek<br><a href='https://bitbucket.org/ckaehler/hubitat/src/master/' target='_blank'>Find more apps on my Bitbucket, just click here!</a><br>${state.version}</div>"
	}       
}