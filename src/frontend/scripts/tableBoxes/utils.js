import React, {Component} from 'react';

export function Field(props)
{
    return (
        <div className="row">
            <div className="col-md-2 fieldTitle">{props.title}:</div>
            <div className="col fieldValue">{props.value}</div>
	</div>	
	);
}

export function FieldNotEmpty(props)
{
    if( !props.value )
        return null
    
    return Field(props)
}

export function FieldWithInnerHTML(props)
{
	return (
		<div className="row">
			<div className="col-md-2 fieldTitle">{props.title}:</div>
			<div className="col fieldValue" dangerouslySetInnerHTML={{__html: props.value}} />
		</div>	
	);
}
