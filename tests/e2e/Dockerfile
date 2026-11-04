FROM mcr.microsoft.com/playwright:v1.64.1-noble

WORKDIR /tests
COPY package.json ./
RUN npm install
COPY playwright.config.js ./
COPY specs ./specs

CMD ["npm", "test"]
