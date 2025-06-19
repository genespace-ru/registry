import ReactDOM     from 'react-dom';
import React        from 'react';
import {AppContainer} from 'react-hot-loader'
import { Provider } from 'react-redux';
import { initBe5App, createBaseStore, rootReducer} from 'be5-react';
import Application from './components/Application';
import './register';


const store = createBaseStore(rootReducer);
const render = Component => {
    ReactDOM.render(
        <AppContainer>
            <Provider store={store}>
                <Component />
            </Provider>
        </AppContainer>,
        document.getElementById('app'),
    )
};

initBe5App(store, function () {
    render(Application);
});

//Webpack Hot Module Replacement API
if (module.hot) {
    module.hot.accept('./components/Application', () => {
        render(Application)
    })
}
