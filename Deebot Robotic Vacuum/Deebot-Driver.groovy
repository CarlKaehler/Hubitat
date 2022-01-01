/*
 *  Deebot Robotic Vacuum
 *
 *  Copyright 2019 Carl Kaehler
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
public static String version() { return "1.0.1" }
 
metadata {
	definition (name: "Deebot Robotic Vacuum", namespace: "Compgeek", author: "Carl Kaehler") {
		capability "Actuator"
		capability "Sensor"
		capability "Battery"
		capability "Refresh"
		capability "Switch"
        
		attribute "lastCheckin", "string"
		attribute "clean_status", "string"
		attribute "charge_status", "string"
		attribute "vacuum_status", "string"
		attribute "fan_speed", "string"
		attribute "main_brush", "number"
		attribute "side_brush", "number"
		attribute "filter", "number"
	}
	
	def _refreshRate = [:]
		_refreshRate << ["1" : "Every 1 Minute"]
		_refreshRate << ["5" : "Every 5 Minutes"]
		_refreshRate << ["10" : "Every 10 Minutes"]
		_refreshRate << ["15" : "Every 15 Minutes"]

	preferences {
		input ("refreshRate", "enum", title: "Device Refresh Rate", options: _refreshRate, required: true, displayDuringSetup: true)
		input ("sucksIP", "text", title: "Sucks Server IP", required: true, displayDuringSetup: true)
		input ("sucksPort", "text", title: "Sucks Server Port", required: true, displayDuringSetup: true)
        input ("sucksDeviceId", "number", title: "Sucks Server Device ID", required: true, displayDuringSetup: true)
	}    
}

def installed() {
	log.debug "Executing 'installed()'"
	sendEvent(name: "switch", value: "off")
}

def updated() {
	log.debug "Executing 'updated()'"
	if (sucksIP != null && sucksPort != null && refreshRate != null && sucksDeviceId != null) {
		if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 2000) {
			state.updatedLastRanAt = now()
			log.trace("$device.displayName - updated() called with settings: ${settings.inspect()}")
			refresh()
			startPoll()
		} else {
			log.trace("$device.displayName - updated() ran within the last 2 seconds - skipping")
		}
	}
	else {
		state.alertMessage = "Device has not yet been fully configured. Click the 'Gear' icon, enter data for all fields, and click 'Done'"
		runIn(2, "sendAlert")
	}

}

//	============ Helper Functions ==============================
def getHostAddress() {
	return "${settings.sucksIP}:${settings.sucksPort}"
}

private void sendMessage(message) {
	log.debug "Executing 'sendMessage' ${message}"
	if (sucksIP != null && sucksPort != null) {
        try {
            httpGet("http://${getHostAddress()}/deebot/${message}/${settings.sucksDeviceId}") { resp ->
                if (resp.success) {
                    log.debug "Result Success"
                    if (resp.getStatus() !=200 || resp.getContentType() != "application/json") {
		                log.debug "Invalid Response Recieved - Aborting"
		                return
	                }

                	def status = resp.getStatus()          // => http status code of the response
	                def data = resp.getData()              // => any JSON included in response body, as a data structure of lists and maps

                	state.lastCheckin = new Date().toString()
                	sendEvent(name: 'lastCheckin', value: state.lastCheckin, displayed: false)
                    switch(message) { 
                        case "clean": 
		                    log.debug "Starting Clean"
                    		refresh()
                            break
                        case "charge":
                            log.debug "Starting Charge"
                            refresh()
                            break
                        case "status":
                    		log.debug "Refreshing Device ..."
                    	    handleStatus(data)
                            break
                        default:
                            break
                    }                    
                }
                if (logEnable)
                    if (resp.data) log.debug "${resp.data}"
            }
        } catch (Exception e) {
            log.warn "Call to on failed: ${e.message}"
        }
	}
	else {
		state.alertMessage = "Device has not yet been fully configured. Click the 'Gear' icon, enter data for all fields, and click 'Done'"
		runIn(2, "sendAlert")
	}
}

private void handleStatus(data) {
    if (state.lastData && (data == state.lastData)) {
    	log.debug "${device.displayName} - no new data"
        sendEvent(name: 'lastUpdate', value: state.lastCheckin, displayed: false) // dummy event for health check
	    return
	}
		
//	if (state.lastData == null || (state.lastData && (data.switch != state.lastData.switch))) {
//		state.switch = data.switch ? "on" : "off"
//		sendEvent(name: 'switch', value: state.switch, displayed: false)
//	}

	if (state.lastData == null || (state.lastData && (data.clean_status != state.lastData.clean_status))) {
		state.clean_status = data.clean_status.capitalize()
		sendEvent(name: 'clean_status', value: state.clean_status, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.charge_status != state.lastData.charge_status))) {
		state.charge_status = data.charge_status.capitalize()
		sendEvent(name: 'charge_status', value: state.charge_status, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.battery_status != state.lastData.battery_status))) {
		state.battery = (data.battery_status * 100).toInteger()
		sendEvent(name: 'battery', value: state.battery, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.vacuum_status != state.lastData.vacuum_status))) {
		state.vacuum_status = data.vacuum_status.capitalize()
		sendEvent(name: 'vacuum_status', value: state.vacuum_status, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.fan_speed != state.lastData.fan_speed))) {
		state.fan_speed = data.fan_speed.capitalize()
		sendEvent(name: 'fan_speed', value: state.fan_speed, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.components.main_brush != state.lastData.components.main_brush))) {
		state.main_brush = (data.components.main_brush * 100).toInteger()
		sendEvent(name: 'main_brush', value: state.main_brush, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.components.side_brush != state.lastData.components.side_brush))) {
		state.side_brush = (data.components.side_brush * 100).toInteger()
		sendEvent(name: 'side_brush', value: state.side_brush, displayed: false)
	}

	if (state.lastData == null || (state.lastData && (data.components.filter != state.lastData.components.filter))) {
		state.filter = (data.components.filter * 100).toInteger()
		sendEvent(name: 'filter', value: state.filter, displayed: false)
	}
		
	state.lastData = data
}

private String createNetworkId(ipaddr, port) {
	if (state.mac) {
		return state.mac
	}
	def hexIp = ipaddr.tokenize('.').collect {
		String.format('%02X', it.toInteger())
	}.join()
	def hexPort = String.format('%04X', port.toInteger())
	return "${hexIp}:${hexPort}"
}

//	============ Deebot Control/Status Methods =================
def on() {
	sendMessage("clean")
}

def off() {
	sendMessage("charge")
}

def refresh() {
    sendMessage("status")
}

/*def ping() {
	log.debug("$device.displayName - checking device health ...")
	sendMessage("status/0")
}*/

//	=========== Functions for SmartApp ==================
def startPoll() {
	unschedule()
	// Schedule polling based on preference setting
	def sec = Math.round(Math.floor(Math.random() * 60))
	def min = Math.round(Math.floor(Math.random() * settings.refreshRate.toInteger()))
	def cron = "${sec} ${min}/${settings.refreshRate.toInteger()} * * * ?" // every N min
	log.trace("$device.displayName - startPoll: schedule('$cron', refresh)")
	schedule(cron, refresh)
}

def setSucksIp(sucksIp) {
	updateDataValue("sucksIp", sucksIp)
	log.info "${device.name} Sucks IP set to ${sucksIp}"
}

def setSucksPort(sucksPort) {
	updateDataValue("sucksPort", sucksPort)
	log.info "${device.name} Sucks Port set to ${sucksPort}"
}

def setSucksDeviceId(sucksDeviceId) {
    updateDataValue("sucksDeviceId", sucksDeviceId)
    log.info "${device.name} Sucks Device ID set to ${sucksDeviceId}"
}

private sendAlert() {
   sendEvent(
      descriptionText: state.alertMessage,
	  eventType: "ALERT",
	  name: "childDeviceCreation",
	  value: "failed",
	  displayed: true,
   )
}
