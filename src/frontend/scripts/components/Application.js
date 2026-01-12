import React from 'react';
import {MAIN_DOCUMENT, NavbarMenuContainer, Document, Be5Components} from 'be5-react';


class Application extends React.Component
{
    render() {
        return (
            <div>
                <Be5Components/>
                <NavbarMenuContainer brand='Genespace registry' languageBox={false}/>
                <div className="container">
                    <div className="row">
                        <Document frontendParams={{documentName: MAIN_DOCUMENT}} />
                    </div>
                </div>
            </div>
        );

    }

}

export default Application;
