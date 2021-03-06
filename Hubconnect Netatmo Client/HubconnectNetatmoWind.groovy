/*
 *	Copyright 2019 Steve White
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not
 *	use this file except in compliance with the License. You may obtain a copy
 *	of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *	WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *	License for the specific language governing permissions and limitations
 *	under the License.
 *
 *
 */
def getDriverVersion() {[platform: "Universal", major: 1, minor: 0, build: 0]}

metadata
{
	definition(name: "HubConnect Netatmo Wind", namespace: "Compgeek", author: "Carl Kaehler")
	{
		capability "Sensor"
		capability "Battery"
		capability "Refresh"

		attribute "windSpeed", "number"
		attribute "windAngle", "number"
		attribute "windDirection", "string"
		attribute "currentWinds", "string"

		attribute "gustSpeed", "number"
		attribute "gustAngle", "number"
		attribute "gustDirection", "string"
		attribute "currentGusts", "string"

		attribute "maximumWindSpeed", "number"
		attribute "maximumWindDate", "string"
		attribute "maximumWindSpeedToday", "string"

		attribute "lastUpdated", "string"
		attribute "version", "string"

		attribute "Summary", "string"
		
		command "sync"
	}
}


/*
	installed

	Doesn't do much other than call initialize().
*/
def installed()
{
	initialize()
}


/*
	updated

	Doesn't do much other than call initialize().
*/
def updated()
{
	initialize()
}


/*
	initialize

	Doesn't do much other than call refresh().
*/
def initialize()
{
	refresh()
}


/*
	uninstalled

	Reports to the remote that this device is being uninstalled.
*/
def uninstalled()
{
	// Report
	parent?.sendDeviceEvent(device.deviceNetworkId, "uninstalled")
}


/*
	parse

	In a virtual world this should never be called.
*/
def parse(String description)
{
	log.trace "Msg: Description is $description"
}


/*
	refresh

	Refreshes the device by requesting an update from the client hub.
*/
def refresh()
{
	// The server will update status
	parent.sendDeviceEvent(device.deviceNetworkId, "refresh")
}


/*
	sync

	Synchronizes the device details with the parent.
*/
def sync()
{
	// The server will respond with updated status and details
	parent.syncDevice(device.deviceNetworkId, "netatmowxwind")
	sendEvent([name: "version", value: "v${driverVersion.major}.${driverVersion.minor}.${driverVersion.build}"])
}
