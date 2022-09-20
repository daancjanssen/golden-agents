# Mock Backend

The goal of this mock backend if to have a very simple pub/sub server running to test/pilot our React frontend. Too keep things simple and lean we have created a Flask app that runs on port 3000.

This backend is based on the one that [can be found here](https://medium.com/@adrianhuber17/how-to-build-a-simple-real-time-application-using-flask-react-and-socket-io-7ec2ce2da977).

### Requirements

* Python 3-something
* flask
* flask-cors
* flask-socketio
* gevent-websocket
* eventlet (this one was pretty weird since it all seemed to work, but any connection attempt was refused. After installing eventlet I was able to establish a connection.)

Installation:
```
$ pip install flask
$ pip install flask-socketio
$ pip install flask-cors
$ pip install gevent-websocket
$ pip install eventlet
```

## Running the mock server

It's hard-coded to run on port 5001

```
$ python server.py
```

## Prepping the frontend

Install socket.io packages:

```
frontend $ npm install socket.io
frontend $ npm install socket.io-client
```

All relevant code can be found in App.js and in the `components` folder.
