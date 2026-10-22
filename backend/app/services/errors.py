class ServiceError(Exception):
    code = "SERVICE_ERROR"
    status_code = 400

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


class NotFound(ServiceError):
    code = "NOT_FOUND"
    status_code = 404


class ValidationError(ServiceError):
    code = "VALIDATION_ERROR"
    status_code = 422


class ConflictError(ServiceError):
    code = "VERSION_CONFLICT"
    status_code = 409


class ActionError(ServiceError):
    code = "ACTION_INVALID"
    status_code = 422
