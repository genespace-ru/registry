import React, {Component} from 'react';
import {registerTableBox, processHashUrl, Navs} from 'be5-react';
import {Field, FieldNotEmpty} from './utils';

class ResourceTabTableBox extends Component
{
  render()
  {
    const resource = this.props.value.data.attributes.rows[0];

    this.title = "AAA";//resource.PageTitle.value;
    be5.ui.setTitle(this.title);

    
    return (
        <div className="container">
            <Field  title='Название'            value={resource.Name.value}/>
            <br/>
            <Field  title='Файл'            value={resource.filepath___.value}/>
            <br/>
            <Field  title='Описание'            value={resource.topic___.value}/>
            <br/>
            <Field  title='Создано'             value={resource.creationdate___.value}/>
            <Field  title='Создал'              value={resource.whoinserted___.value}/>
            <FieldNotEmpty  title='Изменено'    value={resource.whomodified___.value}/>
            <FieldNotEmpty  title='Изменил'     value={resource.modificationdate___.value}/>
       </div>);
  }
}

registerTableBox('resourceTab', ResourceTabTableBox);
export default ResourceTabTableBox;