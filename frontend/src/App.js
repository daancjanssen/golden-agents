import React, { useEffect, useState } from 'react';
import logo from './images/logo.svg';
import { Counter } from './features/counter/Counter';
import { ServerStatus } from './features/serverstatus/ServerStatus';

import WebSocketCall from "./components/WebSocketCall";
import { io } from "socket.io-client";

import './styles/App.scss';


function App() {
  const [socketInstance, setSocketInstance] = useState("");
  const [loading, setLoading] = useState(true);
  const [buttonStatus, setButtonStatus] = useState(false);

  const handleClick = () => {
    if (buttonStatus === false) {
      setButtonStatus(true);
    } else {
      setButtonStatus(false);
    }
  };

  useEffect(() => {
    if (buttonStatus === true) {
      const socket = io("http://127.0.0.1:5001", {
        transports: ["websocket"],
        cors: {
          origin: "http://localhost:3000/",
        },
      });

      // setSocketInstance(socket);

      socket.on("connect", (data) => {
        console.log('connected: ', data);
      });

      setLoading(false);

      socket.on("disconnect", (data) => {
        console.log('disconnected: ', data);
      });

      setSocketInstance(socket);

      // clean up
      return function cleanup() {
        socket.disconnect();
      };
    }
  }, [buttonStatus]);




  return (
    <div className="App">
      <header>
        <img src={logo} className="App-logo" alt="logo" />
      </header>
      <p>Hello</p>

      {!buttonStatus ? (
        <button onClick={handleClick}>turn chat on</button>
      ) : (
        <>
          <button onClick={handleClick}>turn chat off</button>
          <div className="line">
            {!loading && <WebSocketCall socket={socketInstance} />}
          </div>
        </>
      )}
    </div>
  );
}

export default App;
