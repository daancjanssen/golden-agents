const DropDown = props => {

    const onChangeHandler = event => {
        console.log('onChange');
    }

    return (
        <select onChange={onChangeHandler}>
            <option>-- choose --</option>
            { props.options.map((option, index) => {
                return (
                    <option 
                        key={index}
                    >
                    { option }
                    </option>
                )
            })};
        </select>
    )

};

export default DropDown;