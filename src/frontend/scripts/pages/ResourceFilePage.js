import React, { useState, useEffect } from 'react';
import { be5, changeDocument, registerPage, fetchTableByUrl} from 'be5-react';
import { createPageValueLocal } from "../utils";

const ResourceFilePage = (props) => {
    // Support both single object and array of objects
    const dataItems = Array.isArray(props.value?.data ) 
        ? props.value.data  
        : props.value?.data 
            ? [props.value.data] 
            : [];
    const [selectedFilepath, setSelectedFilepath] = useState('');
    const [filecontent, setContent] = useState("");
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    
    // Extract unique filepaths from all data items
    const filepaths = dataItems
        .filter(item => item.filepath)
        .filter(item => item.description=="workflow file")
        .map(item => item.filepath);
    // Get selected item data
    const selectedItem = dataItems.find(item => item.filepath === selectedFilepath);
    const resource2version = selectedItem?.resource2version || '';
    const filepath = selectedFilepath ? encodeURIComponent(selectedFilepath) : '';
    
    const fileUrl = filepath 
        ? `webserver/web/content?resource2versions=${resource2version}&filepath=${filepath}` 
        : '';
   //console.log("fileUrl:", fileUrl);
    // Set default selection when data loads
    useEffect(() => {
        if (filepaths.length > 0 && !selectedFilepath) {
            setSelectedFilepath(filepaths[0]);
        }
    }, [filepaths]);
    
    // Fetch content when selection changes
    useEffect(() => {
        let isMounted = true; 
        
        const fetchContent = async () => {
            if (!fileUrl) {
                if (isMounted) {
                    setContent("");
                    setLoading(false);
                }
                return;
            }
            
            try {
                setLoading(true);
                setError(null);
                
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

        fetchContent();

        return () => {
            isMounted = false;
        };
    }, [fileUrl]);
    
    const handleFilepathChange = (e) => {
        setSelectedFilepath(e.target.value);
    };
    
    return (
        <div className="container">
            {/* Filepath Dropdown */}
            {filepaths.length > 0 && (
                <div className="form-group mb-3">
                    <label htmlFor="filepath-select" className="form-label">
                        Выберите файл:
                    </label>
                    <select
                        id="filepath-select"
                        className="form-select"
                        value={selectedFilepath}
                        onChange={handleFilepathChange}
                        disabled={loading}
                    >
                        {filepaths.map((filepath, index) => (
                            <option key={index} value={filepath}>
                                {filepath}
                            </option>
                        ))}
                    </select>
                </div>
            )}
            
            {/* Loading State */}
            {loading && (
                <div className="alert alert-info" role="alert">
                    Загрузка...
                </div>
            )}
            
            {/* Error State */}
            {error && (
                <div className="alert alert-danger" role="alert">
                    Ошибка: {error}
                </div>
            )}
            
            {/* Content Display */}
            {!loading && !error && selectedFilepath && (
                <div className="border p-3 rounded bg-light">
                    <h6 className="mb-2">
                        Файл: <strong>{selectedFilepath}</strong>
                    </h6>
                    <pre 
                        className="mb-0" 
                        style={{ 
                            whiteSpace: 'pre-wrap', 
                            wordBreak: 'break-word',
                            maxHeight: '500px',
                            overflow: 'auto'
                        }}
                    >
                        {filecontent}
                    </pre>
                </div>
            )}
            
            {/* No Files Message */}
            {filepaths.length === 0 && !loading && (
                <div className="alert alert-warning" role="alert">
                    Нет доступных файлов для отображения
                </div>
            )}
        </div>
    );
};

registerPage('resourceFilePage', ResourceFilePage, (frontendParams, params) => {
    //console.log("params:", params);
    let r2v = params?.resource2versions;
    fetchTableByUrl("table/attachments/ResourceFiles/resource2versions=" + r2v, json => {
        //console.log("json.data", json.data);
        const rows = json.data?.attributes?.rows ||{};
        
        let len = rows.length;
        //console.log("len", len);
        //console.log("rows",rows);
        let dataArray = [];
        for(var i= 0; i < len; i++)
        {
            let cells = rows[i];
            //console.log("cells_i", i, cells);
            dataArray.push({
                resource2version: cells.ID.value,
                filepath: cells.filename.value,
                filetype: cells.filetype.value,
                description: cells.description.value
            });
        }
        console.log("dataArray", dataArray);
            
        /*const cells = json.data.attributes.rows[0];
        const data = {
                resource2version: cells.ID.value,
                filepath: cells.filename.value,
                filetype: cells.filetype.value,
                description: cells.description.value
            };*/
        changeDocument(frontendParams.documentName,
          createPageValueLocal('resourceFilePage', dataArray )
        );
      });
      
});

export default ResourceFilePage;