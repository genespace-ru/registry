import React from 'react';
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

export const createPageValueLocal = (actionName, title, id, version) => {
  const data = {
    id: id,
    title: title,
    version: version
  };
  return {
    value: {data: data},
    frontendParams: {type: actionName}
  }
};

export const extractTabNumber = (hash) => {
  const match = hash.match(/^#!([^\/]+)\/\d+(?:\/(\d+))?$/);
  return match ? (match[2] ? Number( match[2] ) : 0) : null;
}
