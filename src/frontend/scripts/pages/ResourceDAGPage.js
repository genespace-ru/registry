import React, { useState, useEffect } from 'react';
import { be5, changeDocument, registerPage } from 'be5-react';
import { createPageValueLocal } from "../utils";

const ResourceDAGPage = (props) => {
  const data = props.value?.data?.attributes || props.value?.data || {};
  const id = data.id;
  const version = data.version;
  const isValid = data.valid;
  
  const imageUrl = `webserver/web/dag?resource=${id}&version=${version}`;
  
  const [imageLoaded, setImageLoaded] = useState(false);
  const [imageError, setImageError] = useState(false);

  useEffect(() => {
    // Сброс состояния при изменении данных
    setImageLoaded(false);
    setImageError(false);
  }, [imageUrl]);

  if (isValid !== 'yes') {
    return (
      <div className="container">
        <div className="alert alert-warning" role="alert">
          Ресурс содержит ошибки, DAG не может быть создан.
        </div>
      </div>
    );
  }

  if (!id || !version) {
    return (
      <div className="container">
        <div className="alert alert-danger" role="alert">
          Missing required parameters: id or version
        </div>
      </div>
    );
  }

  return (
    <div className="container">
      {!imageLoaded && !imageError && (
        <div className="alert alert-info">Loading DAG...</div>
      )}
      
      {imageError && (
        <div className="alert alert-danger" role="alert">
          Ошибка загрузки DAG
        </div>
      )}
      
      <img 
        src={imageUrl} 
        alt="Resource DAG" 
        className="img-fluid"
        onLoad={() => setImageLoaded(true)}
        onError={() => setImageError(true)}
        style={{ display: imageLoaded ? 'block' : 'none' }}
      />
    </div>
  );
};

registerPage('resourceDAGPage', ResourceDAGPage, (frontendParams, params) => {
  //console.log("params:", params);
  
  const data = {
    id: params?.ID,
    version: params?.versionID,
    valid: params?.isValid
  };
  
  changeDocument(
    frontendParams.documentName,
    createPageValueLocal('resourceDAGPage', data)
  );
});

export default ResourceDAGPage;
