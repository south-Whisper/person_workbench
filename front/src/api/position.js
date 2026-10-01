import request from './request'
export const getPositionList = () => request.get('/position/list').then(r => r.data)
export const createPosition = data => request.post('/position', data).then(r => r.data)
export const updatePosition = (id, data) => request.put('/position/' + id, data).then(r => r.data)
export const updatePositionStatus = (id, data) => request.put('/position/' + id + '/status', data).then(r => r.data)
export const getPositionVersions = id => request.get('/position/' + id + '/versions').then(r => r.data)
