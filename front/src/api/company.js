import request from './request'

export const getCompanyList = () => request.get('/company/list').then(response => response.data)
