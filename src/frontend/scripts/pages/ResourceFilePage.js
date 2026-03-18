import React, { useState, useEffect }  from 'react';
import { be5, changeDocument, registerPage} from 'be5-react';
import {createPageValueLocal} from "../utils";

const ResourceFilePage = (props) => {
    const [filecontent, setContent] = useState("default content");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
  const data = props.value.data;
  const id = data.id;
  const res2version = data.resource2version;
  const filepath = encodeURIComponent(data.filepath)
  const fileUrl = `webserver/web/content?resource2versions=${data.resource2versions}&filepath=${filepath}`;
  
  useEffect(() => {
      const fetchContent = async () => {
        try {
          setLoading(true);
          const response = await fetch(fileUrl);
          
          if (!response.ok) {
            throw new Error(`Ошибка HTTP: ${response.status}`);
          }

          const text = await response.text();
          setContent(text);
          setError(null);
        } catch (err) {
            setError(err.message);
            console.error('Ошибка загрузки:', err);
        } finally {
            setLoading(false);
        }
      };

      fetchContent();

      // Cleanup-функция
      return () => {
      };
    }, [fileUrl]);
    return (
      <div className="container">
        {loading && <div>Загрузка...</div>}
            
        {error && (
          <div style={{ color: 'red', margin: '10px 0' }}>
            Ошибка: {error}
          </div>
        )}
        
        {!loading && !error && (
            <div className="border p-3 rounded">
                <pre>{filecontent}</pre>
          </div>
        )}
      </div>
    );
};


registerPage('resourceFilePage', ResourceFilePage, (frontendParams, params) => {
    console.log("params:");
    console.log(params);
    const data = {
        id: params.ID,
        resource2versions: params.resource2versions,
        filepath: params.filepath
      };
    changeDocument(frontendParams.documentName,
        createPageValueLocal('resourceFilePage', data )
    );
});
