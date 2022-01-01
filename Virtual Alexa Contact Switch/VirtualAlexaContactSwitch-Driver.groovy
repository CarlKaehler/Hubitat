metadata {
	definition (name: "Virtual Alexa Contact Switch", namespace: "Compgeek", author: "Carl Kaehler") {
		capability "Sensor"
		capability "Contact Sensor"
        capability "Switch"
	}   
}

def on() {
    sendEvent(name: "contact", value: "open")
    sendEvent(name: "switch", value: "on")
    runIn(10, off)
}

def off() {
    sendEvent(name: "contact", value: "closed")
    sendEvent(name: "switch", value: "off")
}

def installed() {
}