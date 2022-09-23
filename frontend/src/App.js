import React, { useEffect, useState } from 'react';
import logo from './images/logo.svg';
import WebSocketCall from './components/WebSocketCall';
import QueryBuilder from './components/QueryBuilder';
import { io } from 'socket.io-client';

import './styles/App.scss';


function App() {
  const [socketInstance, setSocketInstance] = useState('');
  const [loading, setLoading] = useState(true);
  const [connectionStatus, setConnectionStatus] = useState(false);

  const handleClick = () => {
    if (connectionStatus === false) {
      setConnectionStatus(true);
    } else {
      setConnectionStatus(false);
    }
  };

  const handleSubmit = () => {
    console.log('handling submission', connectionStatus);
    if (connectionStatus) {
      socketInstance.emit('request', { query: 'Sparql query' });
      console.log('emitted');
    }
  }

  useEffect(() => {
    if (connectionStatus === true) {
      const socket = io('http://127.0.0.1:5001', {
        transports: ['websocket'],
        cors: {
          origin: 'http://localhost:3000/',
        },
      });

      setSocketInstance(socket);

      socket.on('connect', (data) => {
        console.log('connected: ', data);
      });

      setLoading(false);

      socket.on('disconnect', (data) => {
        console.log('disconnected: ', data);
      });

      setSocketInstance(socket);

      // clean up
      return function cleanup() {
        socket.disconnect();
      };
    }
  }, [connectionStatus]);




  return (
    <div className='App'>
      <header>
        <img src={logo} className='App-logo' alt='logo' />
      </header>

      {!connectionStatus ? (
        <button onClick={handleClick}>Connect</button>
      ) : (
        <>
          <button onClick={handleSubmit}>Submit Query</button>
          <button onClick={handleClick}>Disconnect</button>
          <div className='line'>
            {!loading && <WebSocketCall socket={socketInstance} />}
          </div>
          <QueryBuilder />
        </>
      )}
    </div>
  );
}

export default App;
