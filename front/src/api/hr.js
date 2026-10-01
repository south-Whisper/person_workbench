import request from './request'

export const getHrList = () => request.get('/hr/list').then(response => response.data)
