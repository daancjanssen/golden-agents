import { useEffect, useState } from 'react';

export default function WebSocketCall({ socket }) {
  const [message, setMessage] = useState('');
  const [messages, setMessages] = useState([]);

  useEffect(() => {
    socket.on('query_results', (data) => {
      console.log('response', data)
      setMessages([...messages, data.data]);
    });
  }, []);

  return (
    <div>
      <h2>Query results</h2>
      <ul>
        {messages.map((message, ind) => {
          return <li key={ind}>{message}</li>;
        })}
      </ul>
    </div>
  );
}