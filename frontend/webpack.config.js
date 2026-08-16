module.exports = {
  module: {
    rules: [
      {
        test: /\.ttf$/,
        type: 'asset/resource'
      }
    ]
  },
  devServer: {
    historyApiFallback: true
  }
};
