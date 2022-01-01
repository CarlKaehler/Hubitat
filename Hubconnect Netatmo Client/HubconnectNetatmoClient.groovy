/*
 *
 * HubConnect Netatmo Remote for Hubitat
 *
 * Copyright 2019-2020 Steve White, Retail Media Concepts LLC.
 *
 * HubConnect for Hubitat is a software package created and licensed by Retail Media Concepts LLC.
 * HubConnect, along with associated elements, including but not limited to online and/or electronic documentation are
 * protected by international laws and treaties governing intellectual property rights.
 *
 * This software has been licensed to you. All rights are reserved. You may use and/or modify the software.
 * You may not sublicense or distribute this software or any modifications to third parties in any way.
 *
 * By downloading, installing, and/or executing this software you hereby agree to the terms and conditions set forth in the HubConnect license agreement.
 * <http://irisusers.com/hubitat/hubconnect/HubConnect_License_Agreement.html>
 *
 * Hubitat is the trademark and intellectual property of Hubitat, Inc. Retail Media Concepts LLC has no formal or informal affiliations or relationships with Hubitat.
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License Agreement
 * for the specific language governing permissions and limitations under the License.
 *
 */
def getAppVersion() {[platform: "Hubitat", major: 1, minor: 0, build: 2]}

import groovy.transform.Field
import groovy.json.JsonOutput

definition(
	name: "HubConnect Netatmo Client",
	namespace: "Compgeek",
	author: "Carl Kaehler",
	description: "Connect Netatmo weather stations to Hubitat..",
	category: "My Apps",
	iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
	iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	singleInstance: true
)


// Preference pages
preferences
{
	page(name: "mainPage")
	page(name: "aboutPage")
	page(name: "connectPage")
	page(name: "settingsPage")
	page(name: "customDevicePage")
	page(name: "dynamicDevicePage")
	page(name: "uninstallPage")
}


// Map containing driver and attribute definitions for each device class
@Field static APP_CONFIG =
[
	platformName:		"Netatmo",
	platformDevUrl:		"http://dev.netatmo.com",
	platformTitle:		"Weather Stations & Devices",
	apiUrl:				"https://api.netatmo.com",
	authURL:			"https://api.netatmo.com/oauth2/authorize",
	tokenURL:			"https://api.netatmo.com/oauth2/token",
	scope:				"read_station",
	successMessage: 	"Sucessfully connected to the Netatmo cloud.",
	failureMessage:		"Could not connect to the Netatmo cloud.",
	deviceGroup:		"hcnetatmowx",
	donateURL:			"https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=T63P46UYH2TJC&source=url",
	hasSettings:		true,
	autoTokenRefresh: 	true,
	staticCallbackURL:	false
]


// Map containing driver and attribute definitions for each device class
@Field static NATIVE_DEVICES =
[
	"NAMain":		[driver: "HubConnect Netatmo Basestation", selector: "hcnetatmowxBasetations", capability: "relativeHumidityMeasurement", prefGroup: "hcnetatmowx", synthetic: true, attr: ["temperature", "humidity", "pressure", "carbonDioxide", "soundPressureLevel", "sound", "lowTemperature", "highTemperature", "temperatureTrend", "pressureTrend", "Summary"]],
	"NAModule1":	[driver: "HubConnect Netatmo Outdoor Module", selector: "hcnetatmowxOutdoor", capability: "relativeHumidityMeasurement", prefGroup: "hcnetatmowx", synthetic: true, attr: ["temperature", "humidity", "lowTemperature", "highTemperature", "temperatureTrend", "battery"]],
	"NAModule2":	[driver: "HubConnect Netatmo Wind", selector: "hcnetatmowxWind", capability: "sensor", prefGroup: "hcnetatmowx", synthetic: true, attr: ["WindStrength", "WindAngle", "GustStrength", "GustAngle", "max_wind_str", "date_max_wind_str", "units", "battery"]],
	"NAModule3":	[driver: "HubConnect Netatmo Rain", selector: "hcnetatmowxRain", capability: "sensor", prefGroup: "hcnetatmowx", synthetic: true, attr: ["rain", "rainSumHour", "rainSumDay", "units", "battery"]],
	"NAModule4":	[driver: "HubConnect Netatmo Additional Module", selector: "hcnetatmowxModule", capability: "relativeHumidityMeasurement", prefGroup: "hcnetatmowx", synthetic: true, attr: ["temperature", "humidity", "carbonDioxide", "lowTemperature", "highTemperature", "temperatureTrend", "battery"]]
]




/**********		START HubConnect Framework-Specific Functions		**********/

/*
	appSettings

	Purpose: Inserts settings into the default framework page.

	Notes: 	This already appears inside of a section().

	Required: No
*/
def appSettings()
{
	input "rainMeasure", "enum", title: "Rain Measurement", description: "Select a unit of measure for the rain sensor.", options: [mm: "Millimeters", in: "Inches"], required: true, defaultValue: "in"
	input "windMeasure", "enum", title: "Wind Measurement", description: "Select a unit of measure for wind speed.", options: [kph: "Kilometers/hour", ms: "Meters/second", mph: "Miles/hour", kts: "Knots"], required: true, defaultValue: "mph"
	input "pressureMeasure", "enum", title: "Pressure Measurement", description: "Select a unit of measure for barometric pressure.", options: [mbar: "Millibar", in: "Inches/mercury"], required: true, defaultValue: "in"
	input "soundLevel", "number", title: "Sound Sensor Measurement", description: "Enter the threshold (dB) when sound is considered to be detected.", required: false, defaultValue: 36
}


/*
	appInitialize

	Purpose: Called at the end of the framework initialize() method.

	Notes: 	This is an optional method.

	Required: No
*/
def appInitialize()
{
	// Setting station refresh to 5 minutes
	if (childDevices?.size() > 0)
	{
		log.info "Setting station refresh to 5 minutes"
		runEvery5Minutes("refreshDevices")
	}
	else log.info "No devices are selected, skipping station refresh"
}


/*
	appInstalled

	Purpose: Called at the end of the framework installed() method.

	Notes: 	This is an optional method.

	Required: No
*/
def appInstalled()
{
	// Default settings aren't set unless the page is visited, so set them here.
	if (!settings.rainMeasure) app.updateSetting("rainMeasure", [type: "enum", value: "in"])
	if (!settings.windMeasure) app.updateSetting("windMeasure", [type: "enum", value: "mph"])
	if (!settings.pressureMeasure) app.updateSetting("pressureMeasure", [type: "enum", value: "in"])
	if (!settings.soundLevel) app.updateSetting("rainMeasure", [type: "number", value: 36])
}


/*
	appLoadDevices

	Purpose: Returns a list of devices in key/value pairs.

	Notes: 	Key must be a unique ID for the device.  Value is displayed to the user.

	Required: Yes
*/
def appLoadDevices()
{
	if (atomicState.loadingDevices) return
	atomicState.loadingDevices = true
	callAPI("/api/getstationsdata", ["get_favorites": true], "parseSelectableNetatmoDevices")
}


/*
	appButtonEventHandler

	Purpose: Framework button handler, processes button events after main handler.

	Required: No
*/
def appButtonEventHandler(btnPressed)
{
	switch(btnPressed)
	{
		// Disconnect from cloud
		case "logOff":
			unschedule()
			break;
	}
}

/**********		END HubConnect Framework-Specific Functions		**********/





/**********			START Netatmo-Specific Functions			**********/
/*
	callAPI

	Purpose:	Helper function to format API calls for the Netatmo platform.

	Notes:		Logging on errors is done by the helper so we don't have to manage them here.
*/
def callAPI(String commandPath, Map query, callbackFunc)
{
	// Use a framework helper to make the all while making sure the oAuth token remains valid
	asynchttpGetWithCallback("${APP_CONFIG.apiUrl}${commandPath}", query, callbackFunc)
}


/*
	parseSelectableNetatmoDevices

	Purpose:	Helper function to format the device lsit from the Netatmo platform into HubConnect format.
*/
def parseSelectableNetatmoDevices(response, data)
{
	// Set the loading devices flag to false so the "loading" page will go away.
	atomicState.loadingDevices = false

	body = parseJson(response.data)?.body

	// Initialize the platform storage
	state.selectableDevices = [:]

	// Populate the map
	body.devices.each
	{
	  device ->
		// Base station
		if (state.selectableDevices["${device.type}"] == null) state.selectableDevices["${device.type}"] = [:]
		state.selectableDevices["${device.type}"] = ["${device._id}": device.station_name]
		device.modules.each
		{
		  module ->
			// Module
			if (state.selectableDevices["${module.type}"] == null) state.selectableDevices["${module.type}"] = [:]
			state.selectableDevices["${module.type}"] = ["${module._id}": module.module_name]
		}
	}
}


/*
	refreshDevices

	Purpose:	Gets the current weather data from the Netatmo platform.
*/
def refreshDevices()
{
	if (enableDebug) log.info "refreshing devices..."
	callAPI("/api/getstationsdata", ["get_favorites": true], "sendDeviceEvents")
}


/*
	refresh

	Purpose:	Gets the current vehicle data from the Automatic platform.

	Parameters:	cloudDNI	(String) Not used.
*/
def refresh(cloudDNI)
{
	refreshDevices()
}


/*
	sync

	Purpose:	Call refresh since the device is local (and not "HubConnected").

	Parameters:	cloudDNI	(String) Not used.
*/
def syncDevice(String cloudDNI, String groupname)
{
	refreshDevices()
}


/*
	sendDeviceEvents

	Purpose:	Callback from refreshDevices which parses and distributes weather data from Netatmo.
*/
def sendDeviceEvents(response, data)
{
	Map body
	try
	{
		body = parseJson(response.data)?.body
	}
	catch (errorException)
	{
		log.error "Unable to parse the reponse from the Netatmo cloud."
		return
	}

	// Loop through all devices and update the child device attributes
	body.devices.each
	{
	  device ->
		def d = device.dashboard_data

		// Base station
		def child = childDevices.find{it.deviceNetworkId == formatDNI(device._id)}
		if (child && child?.typeName == "HubConnect Netatmo Basestation")
		{
			// Temperature
			child.sendEvent(name: "temperature", value: formatT(d.Temperature), unit: getTemperatureScale())
			child.sendEvent(name: "lowTemperature", value: formatT(d.min_temp), unit: getTemperatureScale())
			child.sendEvent(name: "highTemperature", value: formatT(d.max_temp), unit: getTemperatureScale())
			child.sendEvent(name: "lowTemperatureToday", value: "${formatT(d.min_temp)}° ${formatTs(d.date_min_temp)}", unit: "", isStateChange: false)
			child.sendEvent(name: "highTemperatureToday", value: "${formatT(d.max_temp)}° ${formatTs(d.date_max_temp)}", unit: "", isStateChange: false)
			child.sendEvent(name: "temperatureTrend", value: formatTrend(d.temp_trend), unit: "")

			// Barometer
			child.sendEvent(name: "pressure", value: formatP(d.Pressure), unit: pressureMeasure)
			child.sendEvent(name: "pressureTrend", value: formatTrend(d.pressure_trend), unit: "")
			child.sendEvent(name: "barometer", value: "${formatP(d.Pressure)}${pressureMeasure?.replace("in", "\"")} ${formatTrend(d.pressure_trend)}", unit: "")

			//Environmental
			child.sendEvent(name: "carbonDioxide", value: d.CO2, unit: "ppm")
			child.sendEvent(name: "humidity", value: d.Humidity, unit: "%")
			child.sendEvent(name: "soundPressureLevel", value: d.Noise, unit: "db")
			child.sendEvent(name: "sound", value: toSoundSensor(d.Noise))

			// Housekeeping
			child.sendEvent(name: "lastUpdated", value: formatTs(d.time_utc), unit: "", isStateChange: false, displayed: false)

			// Summary
			def mainSummary = "<div style='line-height: 1; font-size: 1em;'>" + "<br>" + 
				"Indoor:&nbsp;" + formatT(d.Temperature) + "&deg;" + getTemperatureScale() + "&nbsp;-&nbsp;" + d.temp_trend + "<br>" + "<div style='line-height:50%;'><br></div>" + 
				"Min&nbsp;/&nbsp;Max:&nbsp;" + formatT(d.min_temp) + "&deg;" + getTemperatureScale() + "&nbsp;/&nbsp;" + formatT(d.max_temp) + "&deg;" + getTemperatureScale() + "<br>" + "<div style='line-height:50%;'><br></div>" +
				"Humidity:&nbsp;" + d.Humidity + "%&nbsp;&nbsp;" + "CO2:&nbsp;" + d.CO2 + "ppm" + "<br>" + "<div style='line-height:50%;'><br></div>" +
				"ATM:&nbsp;" + (formatP(d.Pressure)).toDouble().trunc(2) + pressureMeasure + "&nbsp;&nbsp;SPL:&nbsp;" + d.Noise + "db" + "<br>" +
				"</div>"
			child.sendEvent(name: 'Summary', value: mainSummary, displayed: false)
		}

		// Add-on sensor modules
		device.modules.each
		{
		  module ->
			def m = module.dashboard_data
			log.trace m
			child = childDevices.find{it.deviceNetworkId == formatDNI(module._id)}
			if (child)
			{
				switch(child.typeName)
				{
					case "HubConnect Netatmo Outdoor Module":
						// Temperature
						child.sendEvent(name: "temperature", value: formatT(m.Temperature), unit: getTemperatureScale())
						child.sendEvent(name: "lowTemperature", value: formatT(m.min_temp), unit: getTemperatureScale())
						child.sendEvent(name: "highTemperature", value: formatT(m.max_temp), unit: getTemperatureScale())
						child.sendEvent(name: "lowTemperatureToday", value: "${formatT(m.min_temp)}° ${formatTs(m.date_min_temp)}", unit: "", isStateChange: false)
						child.sendEvent(name: "highTemperatureToday", value: "${formatT(m.max_temp)}° ${formatTs(m.date_max_temp)}", unit: "", isStateChange: false)
						child.sendEvent(name: "temperatureTrend", value: formatTrend(m.temp_trend), unit: "")

						// Environmentals
						child.sendEvent(name: "humidity", value: m.Humidity, unit: "%")

						// Housekeeping
						child.sendEvent(name: "lastUpdated", value: formatTs(m.time_utc), unit: "", isStateChange: false, displayed: false)
						child.sendEvent(name: "battery", value: module.battery_percent, unit: "%")

						// Summary
						def outdoorSummary = "<div style='line-height: 1; font-size: 1em;'>" + "<br>" + 
							"Outdoor: " + formatT(m.Temperature) + "&deg;" + getTemperatureScale() + " - " + m.temp_trend + "<br>" + "<div style='line-height:50%;'><br></div>" +
							"Minimum: " + formatT(m.min_temp) + "&deg;" + getTemperatureScale() + "<br>" + "<div style='line-height:50%;'><br></div>" +
							"Maximum: " + formatT(m.max_temp) + "&deg;" + getTemperatureScale() + "<br>" + "<div style='line-height:50%;'><br></div>" + 
							"Humidity: " + m.Humidity + "%" + "<br>" + "<div style='line-height:50%;'><br></div>" +
							"Battery: " + module.battery_percent + "%<br>" + "<div style='line-height:50%;'><br></div>" +
							"</div>"
						child.sendEvent(name: 'Summary', value: outdoorSummary, displayed: false)
						break

					case "HubConnect Netatmo Wind":
						// Wind speed
						child.sendEvent(name: "windSpeed", value: formatW(m.WindStrength), unit: settings.windUnits)
						child.sendEvent(name: "windAngle", value: m.WindAngle, unit: "°", displayed: false)
						child.sendEvent(name: "windDirection", value: formatWd(m.WindAngle, true))
						child.sendEvent(name: "currentWinds", value: formatW(m.WindStrength, true), displayed: false)

						// Wind gusts
						child.sendEvent(name: "gustSpeed", value: formatW(m.GustStrength), unit: settings.windUnits)
						child.sendEvent(name: "gustAngle", value: m.GustAngle, unit: "°", displayed: false)
						child.sendEvent(name: "gustDirection", value: formatWd(m.GustAngle, true))
						child.sendEvent(name: "currentGusts", value: formatW(m.GustStrength, true), displayed: false)

						// Extremes
						child.sendEvent(name: "maximumWindSpeed", value: formatW(m.max_wind_str), unit: settings.windUnits)
						child.sendEvent(name: "maximumWindDate", value: formatTs(m.date_max_wind_str), unit: "")
						child.sendEvent(name: "maximumWindSpeedToday", value: formatW(m.max_wind_str, true), displayed: false)

						// Housekeeping
						child.sendEvent(name: "lastUpdated", value: formatTs(m.time_utc), unit: "", isStateChange: false, displayed: false)
						child.sendEvent(name: "battery", value: module.battery_percent, unit: "%")
						
						// Summary
						def windAngleIcon = "<div class='weatherDirection' style='transform: rotate(" + m.WindAngle.toString() + "deg)'><i class='material icons he-arrow-up2'></i></div>"
						def gustAngleIcon = "<div class='weatherDirection' style='transform: rotate(" + m.GustAngle.toString() + "deg)'><i class='material icons he-arrow-up2'></i></div>"
						def windSummary = "<div style='line-height: 1; font-size: 1em;'>" + "<br>" + "<div style='line-height:50%;'><br></div>" +
						"Wind: " + windToPref(m.WindStrength) + "<br>" + windAngleIcon + windTotext(m.WindAngle) + "<br>" + "<div style='line-height:50%;'><br></div>" + 
						"Wind: " + windToPrefUnits(m.WindStrength) + "&nbsp;@" + angleToShortText(m.WindAngle) + windAngleIcon + "<div style='line-height:50%;'><br></div>" + 
						"Gust: " + windToPrefUnits(m.GustStrength) + "&nbsp;@" + angleToShortText(m.GustAngle) + gustAngleIcon + "<div style='line-height:50%;'><br></div>" + 
						"Battery: " + module.battery_percent + "%<br>" + "<div style='line-height:50%;'><br></div>" + "<div style='line-height:50%;'><br></div>" +
						"</div>"
						child.sendEvent(name: 'Summary', value: windSummary, displayed: false)
						break

					case "HubConnect Netatmo Rain":
						child.sendEvent(name: "rain", value: formatR(m.Rain), unit: settings.rainUnits)
						child.sendEvent(name: "rainThisHour", value: formatR(m.sum_rain_1), unit: settings.rainUnits)
						child.sendEvent(name: "rainToday", value: formatR(m.sum_rain_24), unit: settings.rainUnits)
						child.sendEvent(name: "rainfallThisHour", value: formatR(m.sum_rain_1, true), displayed: false)
						child.sendEvent(name: "rainfallToday", value: formatR(m.sum_rain_24, true), displayed: false)

						// Housekeeping
						child.sendEvent(name: "lastUpdated", value: formatTs(m.time_utc), unit: "", isStateChange: false, displayed: false)
						child.sendEvent(name: "battery", value: module.battery_percent, unit: "%")

						// Summary
						def rainSummary = "<div style='line-height: 0.8; font-size: 0.8em;'>" + "<br>" + "Rain: " + formatR(m.sum_rain_1) + "mm / " + formatR(m.sum_rain_24) + "mm<br><div style='line-height:50%;'><br></div>Battery: " + module.battery_percent + "%</div>"
						child.sendEvent(name: 'Summary', value: rainSummary, displayed: false)
						break

					case "HubConnect Netatmo Additional Module":
						// Temperature
						child.sendEvent(name: "temperature", value: formatT(m.Temperature) as float, unit: getTemperatureScale())
						child.sendEvent(name: "lowTemperature", value: formatT(m.min_temp), unit: getTemperatureScale())
						child.sendEvent(name: "highTemperature", value: formatT(m.max_temp), unit: getTemperatureScale())
						child.sendEvent(name: "temperatureTrend", value: formatTrend(m.temp_trend), unit: "")
						child.sendEvent(name: "lowTemperatureToday", value: "${formatT(m.min_temp)}° ${formatTs(m.date_min_temp)}", unit: "", isStateChange: false)
						child.sendEvent(name: "highTemperatureToday", value: "${formatT(m.max_temp)}° ${formatTs(m.date_max_temp)}", unit: "", isStateChange: false)

						// Environmental
						child.sendEvent(name: "carbonDioxide", value: m.CO2, unit: "ppm")
						child.sendEvent(name: "humidity", value: m.Humidity, unit: "%")

						// Housekeeping
						child.sendEvent(name: "lastUpdated", value: formatTs(m.time_utc), unit: "", isStateChange: false, displayed: false)
						child.sendEvent(name: "battery", value: module.battery_percent, unit: "%")

						// Summary
						def additionalSummary = "<div style='line-height: 1; font-size: 1em;'>" + "<br>" + 
							"Battery: " + module.battery_percent + "%<br>" + "<div style='line-height:50%;'><br></div>" + "<div style='line-height:50%;'><br></div>" +
							"Indoor:&nbsp;" + formatT(m.Temperature) + "&deg;" + getTemperatureScale() + "&nbsp;-&nbsp;" + m.temp_trend + "<br>" + "<div style='line-height:50%;'><br></div>" + 
							"Min&nbsp;/&nbsp;Max:&nbsp;" + formatT(m.min_temp) + "&deg;" + getTemperatureScale() + "&nbsp;/&nbsp;" + formatT(m.max_temp) + "&deg;" + getTemperatureScale() + "<br>" + "<div style='line-height:50%;'><br></div>" + 
							"Humidity:&nbsp;" + m.Humidity + "%&nbsp;&nbsp;" + "CO2:&nbsp;" + m.CO2 + "ppm" + "<br>" + "<div style='line-height:50%;'><br></div>" +
							//"ATM:&nbsp;" + (pressToPref(m.Pressure)).toDouble().trunc(2) + settings.pressUnits + "&nbsp;&nbsp;SPL:&nbsp;" + data['Noise'] + "db" + "<br>" +
						"</div>"
					child?.sendEvent(name: 'Summary', value: additionalSummary, displayed: false)
						break
				}
			}
		}
	}
}

// Conversion Functions
Float formatT(t) {getTemperatureScale() == "C" ? t : (t * 1.8 + 32)}
Float formatP(p) {(pressureMeasure == "mbar" ? p : (p * 0.029530)).toDouble().trunc(2)}
String toSoundSensor(n) {n > soundLevel ? "detected" : "not detected"}
String formatTs(ts, ds = false) {new Date((long)ts*1000).format((ds ? "MM-dd-yyyy " : "")+(location.timeFormat == "24" ? "HH:mm" : "h:mm aa"), location.timeZone)}
def formatR(r, l = false)
{
	def rain = ((rainMeasure == "mm" ? r.toDouble().trunc(1) : (r * 0.039370)).toDouble().trunc(3))
	return l ? "${rain} ${rainMeasure}" : rain
}
def formatW(w, l = false)
{
	switch(windMeasure)
	{
		case "ms":
			w = (w * 0.277778).toDouble().trunc(1)
			break
		case "mph":
			w = (w * 0.621371192).toDouble().trunc(1)
			break
		case "kts":
			w = (w * 0.539956803).toDouble().trunc(1)
			break
	}
	l ? "${w} ${windMeasure}" : w
}
String formatWd(Integer wd, Boolean abbv = false)
{
	Map compass =
	[
		23: 	"North",
		68:		"North East",
		113:	"East",
		158:	"South East",
		203:	"South",
		248:	"South West",
		293:	"West",
		338:	"NorthWest",
		361:	"North"
	]
	String windDir = compass.find{deg, dir -> wd < deg}?.value
	abbv ? "${wd}° " + windDir.split(" ").collect{it.charAt(0)}?.join() : "${wd}° ${windDir}"
}
String formatTrend(String t){t == "up" ? "rising" : t == "down" ? "falling" : t}
/**********			END Netatmo-Specific Functions			**********/



/*
 *
 * HubConnect oAuth Application Framework for Hubitat
 *
 * Copyright 2019-2020 Steve White, Retail Media Concepts LLC
 *
 * Please do not remove, modify, or alter this credit in any way.
 *
 */

/*
	mainPage

	Purpose: Displays the main (landing) page.

	Notes: 	Not very exciting.
*/
def mainPage()
{
	atomicState.loadingDevices = false
	if (settings?.clientId == null && state?.installedVersion == null && state?.sessionToken == null) return connectPage()
	if (state.installedVersion != null && state.installedVersion != appVersion) return upgradePage()

	dynamicPage(name: "mainPage", title: "${app.label}${state.commDisabled ? " <span style=\"color:orange\"> [Paused]</span>" : ""}", uninstall: false, install: true)
	{
		section(menuHeader("Connect"))
		{
			href "connectPage", title: "Connect to ${APP_CONFIG.platformName}...", description: "", state: state?.sessionToken ? "complete" : null
			if (state?.sessionToken) href "dynamicDevicePage", title: "Select ${APP_CONFIG.platformName} devices...", description: childDevices?.size() ? "Connected to ${childDevices?.size() - (useProxy ? 1 : 0)} device(s)...  Click to change." : null, state: devicePageStatus."${APP_CONFIG.deviceGroup}" ? "complete" : null, params: [prefGroup: APP_CONFIG.deviceGroup, title: APP_CONFIG.platformTitle]
		}
		section(menuHeader("Admin"))
		{
			if (APP_CONFIG.hasSettings) href "settingsPage", title: "Configure ${APP_CONFIG.platformName} settings...", description: "", state: null
			href "uninstallPage", title: "Disconnect from ${APP_CONFIG.platformName} and remove this instance...", description: "", state: null
			input "enableDebug", "bool", title: "Enable debug output?", required: false, defaultValue: false
		}
		section()
		{
			href "aboutPage", title: "Help Support HubConnect!", description: "HubConnect is provided free of charge for the benefit the Hubitat community.  If you find HubConnect to be a valuable tool, please help support the project."
			paragraph "<span style=\"font-size:.8em\">Remote Client v${appVersion.major}.${appVersion.minor}.${appVersion.build} ${appCopyright}</span>"
		}
	}
}


/*
	connectPage

	Purpose: Connect to the cloud.
*/
def connectPage()
{
	if (state.accessToken == null)
	{
		try
		{
			state.accessToken = createAccessToken()
		}
		catch (errorException)
		{
			log.error "oAuth is disabled for this app.  Please enable it in the apps source code page."
		}
	}

	if (state.accessToken != null) state.oAuthState = UUID.randomUUID().toString()

	// Build the oAuth Request
	String oAuthQS = ""
	if (clientId && clientSecret)
	{
		Map oauthRequestParams =
		[
			client_id: clientId,
			response_type: "code",
			scope: APP_CONFIG.scope,
			state: state.oAuthState
		]
		if (!APP_CONFIG.staticCallbackURL) oauthRequestParams << [redirect_uri: getFullApiServerUrl() + "/oauth/callback?access_token=${state.accessToken}"]
		oAuthQS = queryString(oauthRequestParams)
	}

	dynamicPage(name: "connectPage", uninstall: (state?.sessionToken == null) ? true : false, install: (state?.installedVersion == null ? true : false), nextPage: "mainPage")
	{
		section(menuHeader("${APP_CONFIG.platformName} API Credentials"))
		{
			if (state.accessToken == null)
			{
				paragraph "<b style=\"color:red\">Error: oAuth is not enabled!  Please enable oAuth in the apps code page.</b>"
			}
			else
			{
				paragraph "Please visit the <a href=\"${APP_CONFIG.platformDevUrl}\" target=\"blank\">${APP_CONFIG.platformName} developer center</a> to get your application ID and secret."
				input "clientId", "string", title: "${APP_CONFIG.platformName} Application ID:", required: false, defaultValue: null, submitOnChange: true
				input "clientSecret", "string", title: "${APP_CONFIG.platformName} Application Secret:", required: false, defaultValue: null, submitOnChange: true

				if (APP_CONFIG.staticCallbackURL)
				{
					paragraph "${APP_CONFIG.platformName} requires that callback URL's be configured within their developers portal.\nPlease copy this URL into the applications oAuth Redirect and Application URL:"
					paragraph "<form><input type=\"text\" style=\"font-size:14px;width:100%;\" value=\"${getFullApiServerUrl()}/oauth/callback?access_token=${state.accessToken}\"></form>"
				}

				if (clientId && clientSecret && state.sessionToken == null) href "getAuth", style:"external", title: "Connect to ${APP_CONFIG.platformName}", description: "Connect to the ${APP_CONFIG.platformName} Cloud", url: "${APP_CONFIG.authURL}?${oAuthQS}"
				if (state.sessionToken != null)
				{
					paragraph "<b style=\"color:green\">Connected!</b>"
					if (state?.installedVersion == null) paragraph "Pleae click [Done] to complete installation, then return to the app to select device."
					input "logOff", "button", title: "Disconnect", submitOnChange: true
				}
			}
		}
	}
}


/*
	settingsPage

	Purpose: Displays the settings page.

	Notes: 	Not very exciting.
*/
def settingsPage()
{
	dynamicPage(name: "settingsPage", title: "${APP_CONFIG.platformName}", uninstall: false, install: false)
	{
		if (this.respondsTo("appSettings"))
		{
			section(menuHeader("Settings"))
			{
				// Call the apps initialize()
				appSettings()
			}
		}

		// Call the apps initialize()
		if (this.respondsTo("appExtendedSettings")) appExtendedSettings()

		section()
		{
			href "mainPage", title: "Home", description: "Return to ${APP_CONFIG.platformName} main menu..."
		}
	}
}


/*
	oAuthCallback

	Purpose: Receives the callback following a request for authorization.

	URL Format: (GET) /oauth/callback

	Notes: Called from HTTP request from the remote website.
*/
def oAuthCallback()
{
	// Match the security token (random UUID) before accepting the callback
	if (params.state == state.oAuthState)
	{
		// Build the oAuth Response
		// Scope defines the capabilities that the remote supports
		Map oauthResponseParams =
		[
			grant_type: "authorization_code",
			client_id: clientId,
			client_secret: clientSecret,
			code: params.code,
			scope: APP_CONFIG.scope
		]
		if (!APP_CONFIG.staticCallbackURL) oauthResponseParams << [redirect_uri: getFullApiServerUrl() + "/oauth/callback?access_token=${state.accessToken}"]

		def requestParams =
		[
			uri:  "${APP_CONFIG.tokenURL}",
			contentType: "application/json",
			body: oauthResponseParams,
			timeout: 15
		]

		// Complete the handshake by requesting the access token
		try
		{
			httpPost(requestParams)
			{
			  response ->
				if (response?.status == 200 && response.data)
				{
					state.sessionToken =
					[
						access_token:	response.data.access_token,
						refresh_token:	response.data.refresh_token,
						expires_in:		now() + (response.data.expires_in * 1000), // Timestamp to Microtime
						token_type:		response.data.token_type
					]
				}
				else
				{
					log.error "httpPost() request failed with error ${response?.status}"

					// Force a new token to be generated on failure
					state.accessToken = null
				}
			}
		}
		catch (Exception e)
		{
			log.error "httpPost() failed with error ${e.message}"

			// Force a new token to be generated on failure
			state.accessToken = null
		}

		if (state.sessionToken != null)
		{
			htmlDialog(APP_CONFIG.successMessage)
		}
		else
		{
			htmlDialog(APP_CONFIG.failureMessage)
		}

	}
}


/*
	oAuthRefreshToken

	Purpose: Refreshes the expired token.

	URL Format: (GET) /oauth/callback

	Notes: Called from HTTP request from the remote website.
*/
def oAuthRefreshToken()
{
	// Build the oAuth Request
	// Scope defines the capabilities that the remote supports
	Map oauthRequestParams =
	[
		grant_type: "refresh_token",
		client_id: clientId,
		client_secret: clientSecret,
		refresh_token: state.sessionToken.refresh_token
	]

	def requestParams =
	[
		uri:  "${APP_CONFIG.tokenURL}",
		contentType: "application/json",
		body: oauthRequestParams,
		timeout: 15
	]

	// Complete the handshake by requesting the access token
	try
	{
		httpPost(requestParams)
		{
		  response ->
			if (response?.status == 200 && response.data)
			{
				state.sessionToken =
				[
					access_token:	response.data.access_token,
					refresh_token:	response.data.refresh_token,
					expires_in:		now() + (response.data.expires_in * 1000),	// Timestamp to Microtime
				]
			}
			else
			{
				log.error "httpPost() request failed with error ${response?.status}"

				// Force a new token to be generated on failure
				state.sessionToken = null
			}
		}
	}
	catch (Exception e)
	{
		log.error "httpPost() failed with error ${e.message}"

		// Force a new token to be generated on exception failure
		state.accessToken = null
		state.sessionToken = null
	}
	state.sessionToken ? true : false
}


/*
	htmlDialog

	Purpose: Displays a lightweight html webpage.
*/
def htmlDialog(dialogMessage)
{
	// Generate a lightweight HTML page
	render contentType: "text/html", data: """
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Main Menu</title>
    <meta name="theme-color" content="#ffffff">
    <style>
		* {box-sizing: border-box}
        @font-face {
          font-family: 'hubitat';
          src:  url('/ui2/css/fonts/hubitat.eot?roox2j');
          src:  url('/ui2/css/fonts/hubitat.eot?roox2j#iefix') format('embedded-opentype'),
            url('/ui2/css/fonts/hubitat.woff2?roox2j') format('woff2'),
            url('/ui2/css/fonts/hubitat.ttf?roox2j') format('truetype'),
            url('/ui2/css/fonts/hubitat.woff?roox2j') format('woff'),
            url('/ui2/css/fonts/hubitat.svg?roox2j#hubitat') format('svg');
          font-weight: normal;
          font-style: normal;
          font-display: block;
        }
		.dialogMsg {
			width: 100%;
			padding: 50px;
			text-align: center;
			font-size: 1.2em;
		}
		.dialogMsg img {vertical-align: middle;}
		.dialogMsg input {font-size:1.2em}
    </style>
</head>
<body>
	<div class="dialogMsg">
		<img src="https://cdn.shopify.com/s/files/1/2575/8806/t/20/assets/logo-image-file.png" alt="Hubitat logo" />
	</div>
	<div class="dialogMsg">
		${dialogMessage}
	</div>
	<div class="dialogMsg">
		<form action="javascript:window.close()" method="get">
			<input type="submit" name="submit" value="Close Window" />
		</form>
	</div>
</body>
</html>
"""
}


/*
	dynamicDevicePage

	Purpose: Displays a device selection page.
*/
def dynamicDevicePage(params)
{
	state.saveDevices = true
	dynamicPage(name: "dynamicDevicePage", title: params.title, uninstall: false, install: false, refreshInterval: state?.selectableDevices == null ? 1 : 0)
	{
		if (state?.selectableDevices == null)
		{
			section(menuHeader("Loading..."))
			{
				paragraph "Loading the device list... Please wait."
			}
			appLoadDevices()
			return
		}

		NATIVE_DEVICES.each
		{
		  groupname, device ->
			if (device.prefGroup == params.prefGroup)
			{
				section(menuHeader("Select ${device.driver} Devices (${settings?."${device.selector}"?.size() ?: "0"} connected)"))
				{
					input "${device.selector}", "enum", options: state.selectableDevices[groupname], title: "${device.driver}s:", required: false, multiple: true, defaultValue: null
				}
			}
		}
	}
}


/*
	upgradePage

	Purpose: Displays the splash page to force users to initialize the app after an upgrade.
*/
def upgradePage()
{
	dynamicPage(name: "upgradePage", uninstall: false, install: true)
	{
		section("New Version Detected!")
		{
			paragraph "<b style=\"color:green\">${APP_CONFIG.platformName} has an upgrade that has been installed...</b> <br /> Please click [Done] to complete the installation."
		}
	}
}


/*
	uninstallPage

	Purpose: Displays options for removing an instance.

	Notes: 	Really should create a proper token exchange someday.
*/
def uninstallPage()
{
	dynamicPage(name: "uninstallPage", title: "Uninstall ${APP_CONFIG.platformName}", uninstall: true, install: false)
	{
		section(menuHeader("Warning!"))
		{
			paragraph "It is strongly recommended to back up your hub before proceeding. This action cannot be undone!\n\nClick the [Remove] button below to disconnect and remove this integration."
		}
		section()
		{
			href "mainPage", title: "Cancel and return to the main menu..", description: "", state: null
		}
	}
}


/*
	aboutPage

	Purpose: Displays the about page with credits.
*/
def aboutPage()
{
	dynamicPage(name: "aboutPage", title: "${APP_CONFIG.platformName} v${appVersion.major}.${appVersion.minor}", uninstall: false, install: false)
	{
		section()
		{
			paragraph "${APP_CONFIG.platformName} is provided free for personal and non-commercial use.  If you like it and would like to see it succeed, or see more apps like this in the future, please consider making a small donation."
			href "donate", style:"embedded", title: "Please consider making a \$20 or \$40 donation to show your support!", image: "http://irisusers.com/hubitat/hubconnect/donate-icon.png", url: "${APP_CONFIG.donateURL}"
		}
		section()
		{
			href "mainPage", title: "Home", description: "Return to ${APP_CONFIG.platformName} main menu..."
			paragraph "<span style=\"font-size:.8em\">${APP_CONFIG.platformName} v${appVersion.major}.${appVersion.minor}.${appVersion.build} ${appCopyright}</span>"
		}
	}
}


/*
	saveDevices

	Purpose: Saves cloud devices to the local hub.

	Notes: 	Thank god this isn't SmartThings, or this would time out after creating three devices!
*/
def saveDevices()
{
	// Device cleanup?
	List deviceIdList = childDevices.collect{it.deviceNetworkId}

	// Save the cloud devices
	NATIVE_DEVICES.each
	{
	  groupname, device ->

		// Create the devices
		settings?."${device.selector}"?.each
		{
		  dni ->
			def name = state.selectableDevices["${groupname}"].find {it.key == dni}?.value
			if (name)
			{
				createCloudChildDevice(name, dni, device.driver)
				deviceIdList.removeAll{it == formatDNI(dni)}
			}
			else log.warn "Could not locate a label for the device with DNI of ${dni}."
		}
	}

	// Remove unselected devices
	deviceIdList.each
	{
	  dni ->
		deleteChildDevice(dni)
	}

	state.remove("selectableDevices")
}


/*
	createCloudChildDevice

	Purpose: Helper function to create child devices.

	Notes: 	Called from saveDevices()
*/
private createCloudChildDevice(cloudName, cloudDNI, driverType)
{
	def dni = formatDNI(cloudDNI.replace(" ", "-"))

	def childDevice = getChildDevices()?.find{it.deviceNetworkId == dni}
	if (childDevice)
	{
		// Device exists
		if (enableDebug) log.trace "A device using ${driverType} (${childDevice.deviceNetworkId}) already exists... Skipping creation.."
		return
	}
	else
	{
		if (enableDebug) log.trace "Creating Device ${driverType} - ${cloudName}... ${dni}..."
		try
		{
			childDevice = addChildDevice("shackrat", driverType, dni, null, [name: driverType, label: cloudName])
		}
		catch (errorException)
		{
			log.error "... Uunable to create device ${cloudName}: ${errorException}."
			childDevice = null
		}
		childDevice.updateDataValue("cloudDNI", cloudDNI)
	}
}


/*
	sendDeviceEvent

	Purpose: Wrapper function for the child device command handler.
*/
Map sendDeviceEvent(String deviceDNI, deviceCommand, commandParams=[])
{
	// Get the device
	def childDevice = getChildDevices()?.find{it.deviceNetworkId == deviceDNI}
	if (childDevice == null)
	{
		log.error "Could not locate a device with a network id of ${deviceDNI}"
		return [status: "error"]
	}

	// DeleteSync: Uninstalling device?
	if (deviceCommand == "uninstalled")
	{
		// "de-select" the device
		def newSetting = settings?."${APP_CONFIG.deviceGroup}"?.findResults{if (it.id != "${childDevice.id}") return it.id}
		app.updateSetting("${APP_CONFIG.deviceGroup}", [type: "capability", value: newSetting])

		return [status: "success"]
	}

	if (enableDebug) log.info "Received command from device: [\"${childDevice.label ?: childDevice.name}\": ${deviceCommand}]"

	// Make sure the physical device supports the command
	if (!this.respondsTo(deviceCommand))
	{
		log.warn "The device [${childDevice.label ?: childDevice.name}] does not support the command ${deviceCommand}."
		return [status: "error"]
	}

	// Add the client DNI to the command params
	def cloudDNI = childDevice.getDataValue("cloudDNI")

	// Execute the command
	"${deviceCommand}"(cloudDNI, *commandParams)

	[status: "success"]
}


/*
	appButtonHandler

	Purpose: Handles button events for various pages.
*/
def appButtonHandler(btnPressed)
{
	switch(btnPressed)
	{
		// Disconnect from cloud
		case "logOff":
			state.accessToken = null
			state.sessionToken = null
			unschedule()
			break;
	}

	if (this.respondsTo("appButtonEventHandler")) btnPressed()
}


/*
	getDevicePageStatus

	Purpose: Helper function to set flags for configured devices.
*/
def getDevicePageStatus()
{
	def status = [:]
	NATIVE_DEVICES.each
	{  groupname, device ->
		status["${device.prefGroup}"] = status["${device.prefGroup}"] != null ?: settings?."${device.selector}"?.size()
	}
	status["all"] = status.find{it.value == true} ? true : null
	status
}


/*
	installed

	Purpose: Standard install function.

	Notes: Doesn't do much.
*/
def installed()
{
	log.info "${app.name} Installed"

	state.saveDevices = false
	state.installedVersion = appVersion

	initialize()

	// Call the apps installed() method if defined
	if (this.respondsTo("appInstalled"))
	{
		appInstalled()
	}
}


/*
	updated

	Purpose: Standard update function.

	Notes: Still doesn't do much.
*/
def updated()
{
	log.info "${app.name} Updated"

	initialize()

	// Call the apps updated() method if defined
	if (this.respondsTo("appUpdated"))
	{
		appUpdated()
	}

	state.installedVersion = appVersion
}


/*
	uninstalled

	Purpose: Standard uninstall function.

	Notes: Tries to clean up just in case Hubitat misses something.
*/
def uninstalled()
{
	// Remove all child devices if not explicity told to keep.
	childDevices.each { deleteChildDevice(it.deviceNetworkId) }

	// Call the apps uninstalled() method if defined
	if (this.respondsTo("appUninstalled"))
	{
		appUninstalled()
	}

	log.info "${app.name} has been uninstalled."
}


/*
	initialize

	Purpose: Initialize the server instance.

	Notes:Gets things ready to go!
*/
def initialize()
{
	log.info "${app.name} Initialized"
	unschedule()

   	state.commDisabled = false

	if (state.saveDevices)
	{
		saveDevices()
		state.saveDevices = false
	}

	// Call the apps initialize() if defined
	if (this.respondsTo("appInitialize"))
	{
		appInitialize()
	}

	if (isConnected)
	{
		//runEvery1Minute("appHealth")
	}

	state.remove("selectableDevices")
	//app.updateLabel("${ thisClientName ? thisClientName.replaceAll(/[^0-9a-zA-Z&_]/, "") + "${ isConnected ? '<span style=\"color:green\"> Online</span>' : '<span style=\"color:red\"> Offline</span>' }" : 'HubConnect Remote Client' }")
}


// Mapping to receive events
mappings
{
	// Client mappings
    path("/oauth/callback")
	{
		action: [GET: "oAuthCallback"]
	}
}


/*
	platformHTTPRequest

	Purpose: Helper function to format and make GET/POST requests with the proper oAuth token.

	Notes: 	Returns JSON Map if successful.

*/
def httpGetWithReturn(requestURI, queryString = null, additionalHeaders = [:]) {platformHTTPRequest(requestURI, queryString, additionalHeaders, "httpGet")}
def httpPostWithReturn(requestURI, queryString = null, additionalHeaders = [:]) {platformHTTPRequest(requestURI, queryString, additionalHeaders, "httpPost")}
def platformHTTPRequest(requestURI, additionalHeaders, requestMethod)
{
	// Check token & refresh if expired
	if (APP_CONFIG.autoTokenRefresh && state.sessionToken.expires_in <= (now()-5000)) oAuthRefreshToken()

	def authHeader = state?.sessionToken?.access_token != null ? [Authorization: "Bearer ${state.sessionToken.access_token}"] : [:]
	def requestParams =
	[
		uri:  				requestURI,
		requestContentType: "application/json",
		headers: 			authHeader + additionalHeaders,
		query:				queryString,
		timeout: 			15
	]

	try
	{
		"${requestMethod}"(requestParams)
		{
	  	  response ->
			if (response?.status == 200)
			{
				return response.data
			}
			else
			{
				log.warn "httpGet() request failed with status ${response?.status}"
				return [status: "error", message: "httpGet() request failed with status code ${response?.status}"]
			}
		}
	}
	catch (Exception e)
	{
		log.error "httpGet() failed with error ${e.message}"
		return [status: "error", message: e.message]
	}
	return [status: "success"]
}


/*
	platformAsyncHTTPRequest

	Purpose: Helper function to format and make asynchronous GET/POST requests with the proper oAuth token.

	Notes: 	Returns JSON Map if successful.

*/
def asynchttpGetWithCallback(requestURI, queryString = null, additionalHeaders = [:], callbackFunc,  data = [:]) {platformAsyncHTTPRequest(requestURI, callbackFunc, queryString, additionalHeaders, data, "asynchttpGet")}
def asynchttpPostWithCallback(requestURI, queryString = null, additionalHeaders = [:], callbackFunc,  data = [:]) {platformAsyncHTTPRequest(requestURI, callbackFunc, queryString, additionalHeaders, data, "asynchttpPost")}
def platformAsyncHTTPRequest(requestURI, callbackFunc, queryString, additionalHeaders, data, requestMethod)
{
	// Check token & refresh if expired
	if (APP_CONFIG.autoTokenRefresh && state.sessionToken.expires_in <= (now()-5000)) oAuthRefreshToken()

	def authHeader = state?.sessionToken?.access_token != null ? [Authorization: "Bearer ${state.sessionToken.access_token}"] : [:]
	def requestParams =
	[
		uri:  				requestURI,
		requestContentType: "application/json",
		headers: 			authHeader + additionalHeaders,
		query:				queryString,
		timeout: 			15
	]

	// Use async HTTP with the specified callback
	try
	{
		"${requestMethod}"(callbackFunc, requestParams, data)
	}
	catch (Exception e)
	{
		log.error "httpGet() failed with error ${e.message}"
		return [status: "error", message: e.message]
	}
	return [status: "success"]
}

def formatDNI(String dni) {"${APP_CONFIG.platformName}-${dni}"}
def queryString(Map m) { m.collect { k, v -> "${k}=${URLEncoder.encode(v.toString())}" }.sort().join("&") }
def menuHeader(titleText){"<div style=\"width:102%;background-color:#696969;color:white;padding:4px;font-weight: bold;box-shadow: 1px 2px 2px #bababa;margin-left: -10px\">${titleText}</div>"}
def getIsConnected(){state?.sessionToken?.access_token ? true : false}
def getPref(setting) {return settings."${setting}"}
def getAppCopyright(){"&copy; 2019-2020 Steve White, Retail Media Concepts LLC <a href=\"https://github.com/shackrat/Hubitat-Private/blob/master/HubConnect/License%20Agreement.md\" target=\"_blank\">HubConnect License Agreement</a>"}
