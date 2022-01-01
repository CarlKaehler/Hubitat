/**
 *  ****************  Smart Humidity Fan Parent ****************
 *
 *  Design Usage:
 *  Control a fan (switch) based on relative humidity.
 *
 *  Originally Copyright 2020 csromei
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

def setVersion(){
	if(logEnable) log.debug "In setVersion"
    state.appName = "SmartHumidityFan"
	state.version = "v1.0.0"
}

definition(
    name:"Smart Humidity Fan",
    namespace: "Compgeek",
    author: "Carl Kaehler",
    description: "Control a fan (switch) based on relative humidity.",
    category: "Convenience",
    iconUrl: "https://raw.githubusercontent.com/napalmcsr/SmartThingsStuff/master/smartapps/craig-romei/Bathroom_Fan.jpg",
    iconX2Url: "https://raw.githubusercontent.com/napalmcsr/SmartThingsStuff/master/smartapps/craig-romei/Bathroom_Fan.jpg",
    iconX3Url: "https://raw.githubusercontent.com/napalmcsr/SmartThingsStuff/master/smartapps/craig-romei/Bathroom_Fan.jpg",
    singleInstance: true
)

preferences {
    page name: "mainPage", title: "", install: true, uninstall: true
} 

def installed() {
    log.debug "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.debug "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
}

def initialize() {
    setVersion()
    log.info "There are ${childApps.size()} child apps"
    childApps.each {child ->
    	log.info "Child app: ${child.label}"
    }
}

def mainPage() {
    dynamicPage(name: "mainPage") {
    	installCheck()
        display()
		if(state.appInstalled == 'COMPLETE'){
			section("${getImage('instructions')} <b>Instructions:</b>", hideable: true, hidden: true) {
				paragraph "<b>Information</b>"
				paragraph "<div style='color:#1A77C9'>Control a fan (switch) based on relative humidity.</div>"
			}
			section(getFormat("header-blue", "${getImage("Blank")}"+" Child Apps")) {
				app(name: "anyOpenApp", appName: "Smart Humidity Fan Child", namespace: "Compgeek", title: "<b>Add a new 'Smart Humidity Fan' child</b>", multiple: true)
			}
			section(getFormat("header-blue", "${getImage("Blank")}"+" General")) {
       			label title: "Enter a name for parent app (optional)", required: false
 			}
			display2()
		}
	}
}

def installCheck(){
	state.appInstalled = app.getInstallationState() 
	if(state.appInstalled != 'COMPLETE'){
		section{paragraph "Please hit 'Done' to install '${app.label}' parent app "}
  	}
  	else{
    	log.info "Parent Installed OK"
  	}
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
    section (getFormat("title", "${getImage("logo")}" + " Smart Humidity Fan - ${theName}")) {
		paragraph getFormat("line")
	}
}

def display2(){
	setVersion()
	section() {
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center'>Smart Humidity Fan - @Compgeek<br><a href='https://bitbucket.org/ckaehler/hubitat/src/master/' target='_blank'>Find more apps on my Bitbucket, just click here!</a><br>${state.version}</div>"
	}       
}        
