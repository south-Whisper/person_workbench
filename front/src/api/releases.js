import request from './request'

export const getReleases = () => request.get('/releases').then(response => response.data)
