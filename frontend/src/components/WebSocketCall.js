import { useEffect, useState } from 'react';
import styles from './WebSocketCall.module.css';

export default function WebSocketCall({ socket }) {
  const [messages, setMessages] = useState([]);

  useEffect(() => {
    socket.on('query_results', (data) => {
      console.log('response', data)
      setMessages((prev) => {
        console.log(prev);
        return prev.concat(data.data)});
    });
  }, [socket]);

  return (
    <div>
      <h2>Query results</h2>
      <ul className={styles.no_bullets}>
        {messages.map((message, ind) => {
          return <li key={ind}>{message}</li>;
        })}
      </ul>
    </div>
  );
}