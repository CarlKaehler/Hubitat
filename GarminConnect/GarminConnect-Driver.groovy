public static String version() { return "1.0.1" }

metadata {
	definition (name: "Garmin Connect Device Driver", namespace: "Compgeek", author: "Carl Kaehler") {
		capability "Sensor"
		capability "StepSensor"

        attribute "goal", "number"
        attribute "steps", "number"

        command "setGoal", ["number"]
        command "setSteps", ["number"]
	}   
}

def setGoal(val) {
    sendEvent(name: "goal", value: val)
}

def setSteps(val) {
    sendEvent(name: "steps", value: val)
}

def installed() {
    sendEvent(name: "goal", value: 0)
    sendEvent(name: "steps", value: 0)
}