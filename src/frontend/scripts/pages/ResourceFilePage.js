import React, { useState, useEffect } from 'react';
import { be5, changeDocument, registerPage } from 'be5-react';
import { createPageValueLocal } from "../utils";

const ResourceFilePage = (props) => {
    const [filecontent, setContent] = useState("default content");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    const data = props.value?.data?.attributes || props.value?.data || {};
    const id = data.id;
    const res2version = data.resource2version;
    
    const filepath = data.filepath ? encodeURIComponent(data.filepath) : '';
    
    const fileUrl = `webserver/web/content?resource2versions=${data.resource2versions || ''}&filepath=${filepath}`;
  
    useEffect(() => {
        let isMounted = true; 
        
        const fetchContent = async () => {
            try {
                setLoading(true);
                
                const response = await fetch(fileUrl, {
                    credentials: 'include',
                    headers: {
                        'Accept': 'text/plain'
                    }
                });
                
                if (!response.ok) {
                    throw new Error(`Ошибка HTTP: ${response.status}`);
                }

                const text = await response.text();
                
                const contentType = response.headers.get('Content-Type');
                if (!contentType?.includes('text')) {
                    throw new Error('Неподдерживаемый тип файла');
                }
                
                if (isMounted) {
                    setContent(text);
                    setError(null);
                }
            } catch (err) {
                if (isMounted) {
                    setError(err.message);
                    console.error('Ошибка загрузки:', err);
                }
            } finally {
                if (isMounted) {
                    setLoading(false);
                }
            }
        };

        if (fileUrl) {
            fetchContent();
        }

        return () => {
            isMounted = false;
        };
    }, [fileUrl]);
    
    return (
        <div className="container">
            {loading && <div className="alert alert-info">Загрузка...</div>}
            
            {error && (
                <div className="alert alert-danger" role="alert">
                    Ошибка: {error}
                </div>
            )}
            
            {!loading && !error && (
                <div className="border p-3 rounded bg-light">
                    <pre className="mb-0" style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                        {filecontent}
                    </pre>
                </div>
            )}
        </div>
    );
};

registerPage('resourceFilePage', ResourceFilePage, (frontendParams, params) => {
    //console.log("params:", params);
    const data = {
        id: params?.ID,
        resource2versions: params?.resource2versions,
        filepath: params?.filepath
    };
    
    changeDocument(
        frontendParams.documentName,
        createPageValueLocal('resourceFilePage', data)
    );
});

export default ResourceFilePage;