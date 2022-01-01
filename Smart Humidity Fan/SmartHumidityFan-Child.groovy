/**
 *  ****************  Smart Humidity Fan Child ****************
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
    state.appName = "SmartHumidityFanChild"
	state.version = "v1.0.0"
}

definition(
    name: "Smart Humidity Fan Child",
    namespace: "Compgeek",
    author: "Carl Kaehler",
    description: "Control a fan (switch) based on relative humidity.",
    category: "",
    parent: "Compgeek:Smart Humidity Fan",
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
            paragraph "Choose your Humidty Sensor, Fan and Options."
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Bathroom Devices")) {
			input "HumiditySensor", "capability.relativeHumidityMeasurement", title: "Humidity Sensor:", required: true, displayDuringSetup: true
			input "FanSwitch", "capability.switch", title: "Fan Location:", required: true, displayDuringSetup: true   
            input "OccupiedSwitch", "capability.switch", title: "Occupied Switch:", required: true, displayDuringSetup: true   
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Fan Activation")) {
			input "HumidityIncreaseRate", "number", title: "Humidity Increase Rate :", required: true, defaultValue: 2
			input "HumidityThreshold", "number", title: "Humidity Threshold (%):", required: false, defaultValue: 65
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Fan Deactivation")) {
			input "HumidityDropTimeout", "number", title: "How long after the humidity starts to drop should the fan turn off (minutes):", required: true, defaultValue:  10
			input "HumidityDropLimit", "number", title: "What percentage above the starting humidity before triggering the turn off delay:", required: true, defaultValue:  25
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Manual Activation")) {
			paragraph "When should the fan turn off when turned on manually?"
			input "ManualControlMode", "enum", title: "Off After Manual-On?", required: true, options: ["Manually", "By Humidity", "After Set Time"], defaultValue: "After Set Time"
			paragraph "How many minutes until the fan is auto-turned-off?"
			input "ManualOffMinutes", "number", title: "Auto Turn Off Time (minutes)?", required: false, defaultValue: 20
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Disable Modes")) {
			paragraph "What modes do you not want this to run in?"
			input "modes", "mode", title: "select a mode(s)", multiple: true
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" Logging")) {
			input(
				name: "logLevel"
				,title: "IDE logging level" 
				,multiple: false
				,required: true
				,type: "enum"
				,options: getLogLevels()
				,submitOnChange : false
				,defaultValue : "10"
				) 
        }
        section(getFormat("header-blue", "${getImage("Blank")}"+" General")) {label title: "Enter a name for this child app", required: false, submitOnChange: true}
        display2()
    }
}

// ********** Normal Stuff **********

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
    infolog "Initializing"
	state.OverThreshold = false
	state.AutomaticallyTurnedOn = false
	state.TurnOffLaterStarted = false
    subscribe(HumiditySensor, "humidity", HumidityHandler)
    subscribe(FanSwitch, "switch", FanSwitchHandler)
	subscribe(location, "mode", modeChangeHandler)
}

def modeChangeHandler(evt)
{
	def allModes = settings.modes
	if(allModes)
	{
		if(allModes.contains(location.mode))
		{
			debuglog "modeChangeHandler: Entered a disable mode, turning off the Fan"
			TurnOffFanSwitch()
		}
	} 
	else
	{	
		debuglog "modeChangeHandler: Entered a disable mode, turning off the Fan"
		TurnOffFanSwitch()
	}
}

def HumidityHandler(evt)
{
	infolog "HumidityHandler:running humidity check"
	def allModes = settings.modes
	def modeStop = false
	debuglog "HumidityHandler: state.OverThreshold = ${state.OverThreshold}"
	debuglog "HumidityHandler: state.AutomaticallyTurnedOn = ${state.AutomaticallyTurnedOn}"
	debuglog "HumidityHandler: state.TurnOffLaterStarted = ${state.TurnOffLaterStarted}"               
	debuglog "HumidityHandler: Before"
	debuglog "HumidityHandler: state.lastHumidity = ${state.lastHumidity}"
	debuglog "HumidityHandler: state.lastHumidityDate = ${state.lastHumidityDate}"
	debuglog "HumidityHandler: state.currentHumidity = ${state.currentHumidity}"
	debuglog "HumidityHandler: state.currentHumidityDate = ${state.currentHumidityDate}"
	debuglog "HumidityHandler: state.StartingHumidity = ${state.StartingHumidity}"
	debuglog "HumidityHandler: state.HighestHumidity = ${state.HighestHumidity}"
	debuglog "HumidityHandler: state.HumidityChangeRate = ${state.HumidityChangeRate}"
	debuglog "HumidityHandler: state.targetHumidity = ${state.targetHumidity}"
	state.OverThreshold = CheckThreshold(evt)
	state.lastHumidityDate = state.currentHumidityDate
	if (state.currentHumidity)
	{
		state.lastHumidity = state.currentHumidity
	}
	else
	{
		state.lastHumidity = 100
	}
	if (!state.StartingHumidity)
	{
		state.StartingHumidity = 100
	}
	if (!state.HighestHumidity)
	{
		state.HighestHumidity = 100
	}
	state.currentHumidity = Double.parseDouble(evt.value.replace("%", ""))
	state.currentHumidityDate = evt.date.time
	state.HumidityChangeRate = state.currentHumidity - state.lastHumidity
	if(state.currentHumidity>state.HighestHumidity)
	{
		state.HighestHumidity = state.currentHumidity
	}
	state.targetHumidity = state.StartingHumidity+HumidityDropLimit/100*(state.HighestHumidity-state.StartingHumidity)              
	debuglog "HumidityHandler: After"
	debuglog "HumidityHandler: state.lastHumidity = ${state.lastHumidity}"
	debuglog "HumidityHandler: state.lastHumidityDate = ${state.lastHumidityDate}"
	debuglog "HumidityHandler: state.currentHumidity = ${state.currentHumidity}"
	debuglog "HumidityHandler: state.currentHumidityDate = ${state.currentHumidityDate}"
	debuglog "HumidityHandler: state.StartingHumidity = ${state.StartingHumidity}"
	debuglog "HumidityHandler: state.HighestHumidity = ${state.HighestHumidity}"
	debuglog "HumidityHandler: state.HumidityChangeRate = ${state.HumidityChangeRate.round(2)}"
	debuglog "HumidityHandler: state.targetHumidity = ${state.targetHumidity}"
	//if the humidity is high (or rising fast) and the fan is off, kick on the fan
	if(allModes)
	{
		if(allModes.contains(location.mode))
		{
			modeStop = true
		}
	}
    if (((state.HumidityChangeRate>HumidityIncreaseRate)||state.OverThreshold) && (FanSwitch.currentValue("switch") == "off") && (OccupiedSwitch.currentValue("switch") == "on") && !modeStop)
    {
		state.AutomaticallyTurnedOn = true
		state.TurnOffLaterStarted = false
		state.AutomaticallyTurnedOnAt = new Date().format("yyyy-MM-dd HH:mm")
		infolog "HumidityHandler:Turn On Fan due to humidity increase"
		FanSwitch.on()
        state.StartingHumidity = state.lastHumidity
        state.HighestHumidity = state.currentHumidity    
		debuglog "HumidityHandler: new state.StartingHumidity = ${state.StartingHumidity}"
		debuglog "HumidityHandler: new state.HighestHumidity = ${state.HighestHumidity}"
		debuglog "HumidityHandler: new state.targetHumidity = ${state.targetHumidity}"
	}
	//turn off the fan when humidity returns to normal and it was kicked on by the humidity sensor
	else if((state.AutomaticallyTurnedOn || ManualControlMode == "By Humidity")&& !state.TurnOffLaterStarted)
	{    
        if(state.currentHumidity<=state.targetHumidity)
        {
            if(HumidityDropTimeout == 0)
            {
                infolog "HumidityHandler:Fan Off"
                TurnOffFanSwitch()
            }
            else
            {
				infolog "HumidityHandler:Turn Fan off in ${HumidityDropTimeout} minutes."
				state.TurnOffLaterStarted = true
				runIn(60 * HumidityDropTimeout.toInteger(), TurnOffFanSwitchCheckHumidity)
				debuglog "HumidityHandler: state.TurnOffLaterStarted = ${state.TurnOffLaterStarted}"
			}
		}
	}
}

def FanSwitchHandler(evt)
{
	infolog "FanSwitchHandler::Switch changed"
	debuglog "FanSwitchHandler: ManualControlMode = ${ManualControlMode}"
	debuglog "FanSwitchHandler: ManualOffMinutes = ${ManualOffMinutes}"
	debuglog "HumidityHandler: state.AutomaticallyTurnedOn = ${state.AutomaticallyTurnedOn}"
	switch(evt.value)
	{
		case "on":
			if(!state.AutomaticallyTurnedOn && (ManualControlMode == "After Set Time") && ManualOffMinutes)
			{
				if(ManualOffMinutes == 0)
				{
					debuglog "FanSwitchHandler::Fan Off"
					TurnOffFanSwitch()
				}
					else
				{
					debuglog "FanSwitchHandler::Will turn off later"
					runIn(60 * ManualOffMinutes.toInteger(), TurnOffFanSwitch)
				}
			}
			break
        case "off":
			debuglog "FanSwitchHandler::Switch turned off"
			state.AutomaticallyTurnedOn = false
			state.TurnOffLaterStarted = false
			break
    }
}

def TurnOffFanSwitchCheckHumidity()
{
    debuglog "TurnOffFanSwitchCheckHumidity: Function Start"
	if(FanSwitch.currentValue("switch") == "on")
    {
		debuglog "TurnOffFanSwitchCheckHumidity: state.HumidityChangeRate ${state.HumidityChangeRate}"
		if(state.currentHumidity > state.targetHumidity)
        {
			debuglog "TurnOffFanSwitchCheckHumidity: Didn't turn off fan because humidity rate is ${state.HumidityChangeRate}"
			state.AutomaticallyTurnedOn = true
			state.AutomaticallyTurnedOnAt = now()
			state.TurnOffLaterStarted = false
		}
		else
		{
			debuglog "TurnOffFanSwitchCheckHumidity: Turning the Fan off now"
			TurnOffFanSwitch()
		}
	}
}

def TurnOffFanSwitch()
{
    if(FanSwitch.currentValue("switch") == "on")
    {
        infolog "TurnOffFanSwitch:Fan Off"
        FanSwitch.off()
        state.AutomaticallyTurnedOn = false
        state.TurnOffLaterStarted = false
    }
}

def CheckThreshold(evt)
{
	double lastevtvalue = Double.parseDouble(evt.value.replace("%", ""))
	if(lastevtvalue >= HumidityThreshold)
	{  
		infolog "IsHumidityPresent: Humidity is above the Threashold"
		return true
	}
	else
	{
		return false
	}
}

def debuglog(statement)
{   
	def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 2)
	{
		log.debug(statement)
	}
}
def infolog(statement)
{       
	def logL = 0
    if (logLevel) logL = logLevel.toInteger()
    if (logL == 0) {return}//bail
    else if (logL >= 1)
	{
		log.info(statement)
	}
}
def getLogLevels(){
    return [["0":"None"],["1":"Running"],["2":"NeedHelp"]]
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