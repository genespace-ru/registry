import React, {Component} from 'react';
import {be5, executeFrontendActions, getBackOrOpenDefaultRouteAction} from 'be5-react';

export const getBackOrOpenDefaultRouteActionButton = (props) => {
  return (
    <button
      type="button"
      className="btn btn-light mt-2 btn-back"
      onClick={() => executeFrontendActions(getBackOrOpenDefaultRouteAction(), props.frontendParams)}
    >
      {be5.messages.back}
    </button>
  )
};

export const createPageValueLocal = (actionName, data) => {
  return {
    value: {data: data},
    frontendParams: {type: actionName}
  }
};

export const extractTabNumber = (hash) => {
  const match = hash.match(/^#!([^\/]+)\/\d+(?:\/(\d+))?$/);
  return match ? (match[2] ? Number( match[2] ) : 0) : null;
}

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

