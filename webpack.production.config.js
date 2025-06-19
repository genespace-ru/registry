"use strict";
const webpack = require('webpack');
const path = require('path');
const rules = require('./webpack.common').rules;
const externals = require('./webpack.common').externals;
const HtmlWebpackPlugin = require('html-webpack-plugin');
const WebpackCleanupPlugin = require('webpack-cleanup-plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const OptimizeCssAssetsPlugin = require('optimize-css-assets-webpack-plugin');
const FileManagerPlugin = require('filemanager-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;

rules.push({
    test: /\.scss$/,
    use: [
        MiniCssExtractPlugin.loader,
        {
            loader: 'css-loader',
/*
            options: {
                modules: {
                    localIdentName: '[local]___[hash:base64:5]'
                }
            }
*/
        },
        {
            loader: 'sass-loader',
            options: {
                implementation: require('sass'),
                sassOptions: {outputStyle: 'expanded'}
            }
        }
    ],
});


module.exports = (env) => {
  const baseOutPath = 'src/main/webapp/';
  const outPath = baseOutPath + 'WEB-INF/templates';

  let fileName =     env.min ? 'static/[name]-[hash].min.js' : 'static/[name]-[hash].js';
  let templateName = env.min ? 'template.html' : 'template-dev.html';

  const plugins = [
    //new BundleAnalyzerPlugin(),
    new WebpackCleanupPlugin(),
    new webpack.optimize.OccurrenceOrderPlugin(),
    new MiniCssExtractPlugin({
      filename: 'static/css/[name]+[hash].css',
    }),
    new OptimizeCssAssetsPlugin(),
    new HtmlWebpackPlugin({
      filename: 'index.html',
      fixAssets: true,
      template: './src/frontend/' + templateName,
      chunks: ['commons', 'app'],
      files: {
        css: ['style.css'],
        js: ['bundle.js'],
      }
    }),
    new HtmlWebpackPlugin({
      filename: 'manager/index.html',
      fixAssets: true,
      template: './src/frontend/' + templateName,
      chunks: ['commons', 'manager'],
      files: {
        css: ['style.css'],
        js: ['bundle.js'],
      }
    }),
/*
    removed in webpack 4 use config.optimization.splitChunks instead
    new webpack.optimize.CommonsChunkPlugin({
      name: "commons",
      // (the commons chunk name)

      filename: "static/commons+[hash].js",
      // (the filename of the commons chunk)

      // minChunks: 3,
      // (Modules must be shared between 3 entries)

      // chunks: ["pageA", "pageB"],
      // (Only use these entries)
    }),
*/
    new webpack.ContextReplacementPlugin(/moment[\/\\]locale$/, /en|ru/),
    new FileManagerPlugin({
      onStart: {delete: [baseOutPath + 'static', baseOutPath + 'WEB-INF/templates/']},
      onEnd: {
        move: [{
          source: baseOutPath + 'WEB-INF/templates/static',
          destination: baseOutPath + 'static'
        }],
      }
    })
  ]
  if (env.min) {
    plugins.push(new webpack.DefinePlugin({
      'process.env.NODE_ENV': JSON.stringify('production')
    }));
  }


    return {
        mode: "production",
        optimization: {
            minimize: env.min,
        },
        entry: {
            app: ['babel-polyfill', './src/frontend/scripts/initApp.js'],
            manager: ['babel-polyfill', './src/frontend/scripts/manager.js']
        },
        output: {
            publicPath: '/',
            path: path.join(__dirname, outPath),
            filename: fileName,
            chunkFilename: 'static/app-[name]-[id].js',
            library: '[name]'
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
        plugins: plugins,
        externals: externals,
    }
};
