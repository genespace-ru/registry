"use strict";
const webpack = require('webpack');
const path = require('path');
const rules = require('./webpack.common').rules;
const externals = require('./webpack.common').externals;
const HtmlWebpackPlugin = require('html-webpack-plugin');
const DashboardPlugin = require('webpack-dashboard/plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');

const HOST = process.env.HOST || "127.0.0.1";
const PORT = process.env.PORT || "8888";

rules.push({
    test: /\.scss$/,
    use: [
        'style-loader',
        {loader: 'css-loader',options: {importLoaders: '1'}},
        {loader: 'sass-loader', options: {implementation: require('sass'), sassOptions: {outputStyle: 'expanded'}}}
    ],
})

module.exports = {
    mode: "development",
    entry: [
        'babel-polyfill',
        'react-hot-loader/patch',
        './src/frontend/scripts/initApp.js'
    ],
    devtool: process.env.WEBPACK_DEVTOOL || 'eval-source-map',
    output: {
        publicPath: '/',
        path: path.join(__dirname, 'public'),
        filename: '[name].js',
        library:  '[name]'
    },
    resolve: {
        extensions: ['.js', '.jsx'],
        alias: {
          react: path.resolve('node_modules/react'),
        },
    },
    module: {
        rules
    },
    devServer: {
        contentBase: "./src/frontend",
        // do not print bundle build stats
        noInfo: true,
        // enable HMR
        hot: true,
        // embed the webpack-dev-server runtime into the bundle
        inline: true,
        // serve index.html in place of 404 responses to allow HTML5 history
        historyApiFallback: true,
        port: PORT,
        host: HOST,
        proxy: {
            '/api/*' : {
                target: 'http://localhost:8200/api/',
                secure: false,
                changeOrigin: true,
                pathRewrite: {
                    '^/api': ''
                }
            },
            '/static/*' : {
                target: 'http://localhost:8200/static/',
                secure: false,
                changeOrigin: true,
                pathRewrite: {
                    '^/static': ''
                }
            }
        }
    },
    plugins: [
        new webpack.HotModuleReplacementPlugin(),
        new MiniCssExtractPlugin({
            filename: 'static/[name]+[hash]style.css',
        }),
        new DashboardPlugin(),
        new HtmlWebpackPlugin({
            template: './src/frontend/template-dev.html',
            files: {
                css: ['style.css'],
                js: [ "bundle.js"],
            }
        }),
    ],
    externals: externals,
};
