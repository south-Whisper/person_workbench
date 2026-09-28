import request from './request'
export const loginApi = data => request.post('/auth/login', data).then(r => r.data)
export const getSetupStatus = () => request.get('/auth/setup/status').then(r => r.data)
export const setupApi = data => request.post('/auth/setup', data).then(r => r.data)
