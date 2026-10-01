import request from './request'

export const getEmployeeList = () => request.get('/employee/list').then(response => response.data)
export const changeEmployeeRole = (id, role) => request.put(`/employee/${id}/role`, { role }).then(response => response.data)
export const updateEmployeeAccount = (id, data) => request.put(`/employee/${id}/account`, data).then(response => response.data)
