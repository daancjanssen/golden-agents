import {useState} from 'react';
import DropDown from './DropDown';

const QueryBuilder = props => {

    const [query, setQuery] = useState([
        { type: 'dropdown', options: ['hasA', 'isA', 'knowsA'], value: '' }
    ]);

    const addElement = () => {
        
    }

    const selectUIElement = item => {
        if (item.type == 'dropdown') {
            return <DropDown value={item.value} options={item.options} onClick={addElement} />
        }
    }



    return (
        <ul>
            {query.map((item, index) => <li key={index}>{selectUIElement(item)}</li>)}
        </ul>
    )


}

export default QueryBuilder;