from flask import Flask, request, jsonify, render_template, current_app
from flask_socketio import SocketIO, emit
from flask_cors import CORS
from threading import Lock

thread = None
thread_lock = Lock()

app = Flask(__name__)
app.config['SECRET_KEY'] = 'secret!'
CORS(app,resources={r'/*':{'origins':'*'}})
socketio = SocketIO(app, cors_allowed_origins='*')

def background_response(app):
    """Send every 3 seconds data to frontend"""
    with app.app_context():
        for batch in range(10):
            socketio.sleep(3)
            data = [f'package - { batch * 100 + p }' for p in range(10)]
            print('Emitting...')
            socketio.emit(
                'query_results', 
                { 'data': data, 'id': request.sid }, 
                broadcast=True
            )

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
    emit('connect', { 'data':f'id: {request.sid} is connected' })

@socketio.on('request')
def handle_message(data):
    """event listener for a request"""
    global thread

    print('data from the frontend: ',  str(data))
    # emit('query_results', { 'data': [1, 2, 3, 4, 5], 'id': request.sid }, broadcast=False)
    with thread_lock:
        if thread == None:
            threat = socketio.start_background_task(
                background_response,
                current_app._get_current_object()
            )
    emit('query_results', { 'data': [] })

@socketio.on('disconnect')
def disconnected():
    """event listener when client disconnects to the server"""
    print('user disconnected')
    emit('disconnect',f'user {request.sid} disconnected',broadcast=True)


if __name__ == '__main__':
    print('...Starting backend')
    socketio.run(app, host="127.0.0.1", debug=True, port=5001)