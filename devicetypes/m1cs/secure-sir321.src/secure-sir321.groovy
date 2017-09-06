/**
 *
 *  Horstmann Secure SIR-321
 *
 *  Author: Mike Baird
 *
 *  Date: 2017-08-15
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  http://products.z-wavealliance.org/products/1012
 *
 */
metadata {

	definition (name: "Secure SIR321", namespace: "m1cs", author: "Mike Baird") {
		capability "Actuator"
		capability "Configuration"
		capability "Switch"
		capability "Polling"
		capability "Refresh"
		capability "Sensor"
		capability "Thermostat Schedule"
		capability "Temperature Measurement"
		        
		fingerprint mfr: "0059", prod: "0010", deviceJoinName: "Immersion Heater"		
	}

	// simulator metadata
	simulator {
		status "on":  "command: 2503, payload: FF"
		status "off": "command: 2503, payload: 00"

		// reply messages
		reply "2001FF,delay 100,2502": "command: 2503, payload: FF"
		reply "200100,delay 100,2502": "command: 2503, payload: 00"
	}

	//tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label: 'Heating', action: "switch.off", icon: "st.Bath.bath13", backgroundColor: "#79b821", nextState:"off"
				attributeState "off", label: '${name}', action: "switch.on", icon: "st.Bath.bath13", backgroundColor: "#ffffff", nextState:"on"
			}
			tileAttribute("device.temperature", key: "SECONDARY_CONTROL") {
				attributeState("default", label:'${currentValue}', unit:"C", icon: "st.Weather.weather2")
			}
		}

		standardTile("refresh", "device.switch", width: 2, height: 2, inactiveLabel: false, decoration: "flat") 
		{
			state "default", label:'Refresh', action:"refresh.refresh", icon:"st.secondary.refresh"
		}
        
        //standardTile("configure", "device.configuration", width: 2, height: 2, inactiveLabel: false, decoration: "flat") 
		//{
		//	state "default", label:'Configure', action:"configuration.configure", icon:"st.secondary.refresh"
		//}
        

		main "switch"
		details(["switch","temperature","refresh"])
	}
}

def parse(String description) {
	log.debug "parse description: $description"
	
	def result
	//COMMAND_CLASS_BASIC						0x20
	//COMMAND_CLASS_SWITCH_BINARY				0x25
	//COMMAND_CLASS_VERSION						0x86
	//COMMAND_CLASS_CONFIGURATION				0x70 
	//COMMAND_CLASS_MANUFACTURER_SPECIFIC		0x72 (v2)
	//COMMAND_CLASS_SENSOR_MULTILEVEL			0x31 
	//COMMAND_CLASS_CLIMATE_CONTROL_SCHEDULE	0x46
	//COMMAND_CLASS_ASSOCIATION					0x85
	
	def cmd = zwave.parse(description, [0x20:1, 0x25:1, 0x86:1, 0x70:1, 0x72:2, 0x31:1])
	if (cmd) {
		result = createEvent(zwaveEvent(cmd))
	}
    
	log.debug "Parse returned ${result?.descriptionText}"
	
	return result
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "physical"]
}

def zwaveEvent(physicalgraph.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
	[name: "switch", value: cmd.value ? "on" : "off", type: "digital"]
}

def zwaveEvent(physicalgraph.zwave.commands.hailv1.Hail cmd) {
	[name: "hail", value: "hail", descriptionText: "Switch button was pressed", displayed: false]
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
	if (state.manufacturer != cmd.manufacturerName) {
		updateDataValue("manufacturer", cmd.manufacturerName)
	}
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd)
{	
    log.debug "configuration: $cmd"
    def map = [:]
    map.value = cmd.scaledConfigurationValue
    map.displayed = false
    switch (cmd.parameterNumber) {
        case 1:
        map.name = "failSafeTimer"
        break
        case 2:
        map.name = "tempScale"
        break
        case 3:
        map.name = "tempReportInterval"
        break
        case 4:
        map.name = "deltaConfigTempReport"
        break
        case 5:
        map.name = "tempCutOff"
        break
        default:
            return [:]
    }
    return map
	
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv1.SensorMultilevelReport cmd)
{
	def result = []
	def map = [:]
	map.value = cmd.scaledSensorValue.toString()
	map.unit = cmd.scale == 1 ? "F" : "C"
	map.name = "temperature"
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	debug.log "$cmd"
	// Handles all Z-Wave commands we aren't interested in
	[:]
}

def on() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.configurationV1.configurationSet(scaledConfigurationValue: 60, parameterNumber: 3, size: 2).format() //poll interval reduced to 1 minute
	])
}

def off() {
	delayBetween([
		zwave.basicV1.basicSet(value: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format(),
        zwave.configurationV1.configurationSet(scaledConfigurationValue: 300, parameterNumber: 3, size: 2).format() //poll interval increaced to 5 minutes
	])    
}

def poll() {
	delayBetween([
	zwave.switchBinaryV1.switchBinaryGet().format(),
	zwave.sensorMultilevelV1.sensorMultilevelGet().format()
	])
}

def refresh() {
    delayBetween([
	zwave.switchBinaryV1.switchBinaryGet().format(),
	zwave.sensorMultilevelV1.sensorMultilevelGet().format(),
    zwave.configurationV1.configurationGet(parameterNumber: 1).format(),
    zwave.configurationV1.configurationGet(parameterNumber: 2).format(),
    zwave.configurationV1.configurationGet(parameterNumber: 3).format(),
    zwave.configurationV1.configurationGet(parameterNumber: 4).format(),
    zwave.configurationV1.configurationGet(parameterNumber: 5).format()
    ], 3000)
}

def updated() {
	log.debug "updated.. calling configure."
    configure()
}

def configure() {
	log.debug "configure"
	def cmds = []
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: 0, parameterNumber: 1, size: 1).format() //disable failsafe timer
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: 0, parameterNumber: 2, size: 2).format() //Set Temp Scale to C
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: 300, parameterNumber: 3, size: 2).format() //poll interval in seconds
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: 10, parameterNumber: 4, size: 2).format() //poll interval in degrees
    cmds << zwave.configurationV1.configurationSet(scaledConfigurationValue: 800, parameterNumber: 5, size: 2).format() //degree failsafe in 0.1 degrees (800 = 80 C)
    cmds << zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:[zwaveHubNodeId]).format()
	delayBetween(cmds, 2500)
}