#!/usr/bin/env python3
from flask import Flask
from flask import jsonify
from sucks.cli import *

#read sucks config and get an api connection
config = read_config()
api = EcoVacsAPI(config['device_id'], config['email'], config['password_hash'],
                         config['country'], config['continent'])

app = Flask(__name__)

@app.route('/deebot', methods=['get'])
def index():
  return "Deebot REST API V1.0"

@app.route('/deebot/devices', methods=['get'])
def getDevices():
  return jsonify(api.devices())

@app.route('/deebot/status/<int:deebot_id>', methods=['get'])
def getStatus(deebot_id):
  my_vac = api.devices()[deebot_id]
  vacbot = VacBot(api.uid, api.REALM, api.resource, api.user_access_token, my_vac, config['continent'])
  vacbot.connect_and_wait_until_ready()
  vacbot.request_all_statuses()
  retVal = {}
  retVal['clean_status'] = getattr(vacbot, 'clean_status')
  retVal['charge_status'] = getattr(vacbot, 'charge_status')
  retVal['battery_status'] = getattr(vacbot, 'battery_status')
  retVal['vacuum_status'] = getattr(vacbot, 'vacuum_status')
  retVal['fan_speed'] = getattr(vacbot, 'fan_speed')
  retVal['components'] = getattr(vacbot, 'components')
  vacbot.disconnect(wait=True)
  return jsonify(retVal)

@app.route('/deebot/clean/<int:deebot_id>', methods=['get'])
def clean(deebot_id):
  my_vac = api.devices()[deebot_id]
  vacbot = VacBot(api.uid, api.REALM, api.resource, api.user_access_token, my_vac, config['continent'])
  vacbot.connect_and_wait_until_ready()
  vacbot.run(Clean())
  return jsonify("Success")

@app.route('/deebot/charge/<int:deebot_id>', methods=['get'])
def charge(deebot_id):
  my_vac = api.devices()[deebot_id]
  vacbot = VacBot(api.uid, api.REALM, api.resource, api.user_access_token, my_vac, config['continent'])
  vacbot.connect_and_wait_until_ready()
  vacbot.run(Charge())
  return jsonify("Success")

if __name__ == '__main__':
  app.run(host='0.0.0.0')