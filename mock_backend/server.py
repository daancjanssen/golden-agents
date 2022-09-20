from flask import Flask, request, jsonify, render_template
from flask_socketio import SocketIO,emit
from flask_cors import CORS


app = Flask(__name__)
app.config['SECRET_KEY'] = 'secret!'
CORS(app,resources={r'/*':{'origins':'*'}})
socketio = SocketIO(app, cors_allowed_origins='*')

@app.route('/')
def api():
    """return JSON with string data as the value"""
    data = {'data': 'This text was fetched using an HTTP call to server on render'}
    return render_template('main.html')

@socketio.on('connect')
def connected():
    """event listener when client connects to the server"""
    print(request.sid)
    print('client has connected')
    emit('connect',{ 'data':f'id: {request.sid} is connected' })

@socketio.on('data')
def handle_message(data):
    """event listener when client types a message"""
    print('data from the front end: ',str(data))
    emit('data',{'data':data,'id':request.sid},broadcast=True)

@socketio.on('disconnect')
def disconnected():
    """event listener when client disconnects to the server"""
    print('user disconnected')
    emit('disconnect',f'user {request.sid} disconnected',broadcast=True)


if __name__ == '__main__':
    print('...Starting backend')
    # app.run(debug=True, port=5001)
    socketio.run(app, host="127.0.0.1", debug=True, port=5001)