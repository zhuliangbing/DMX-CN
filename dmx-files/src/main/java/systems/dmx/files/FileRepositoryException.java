package systems.dmx.files;

import javax.ws.rs.core.Response.Status;



class FileRepositoryException extends Exception {

    Status status;

    FileRepositoryException(String message, Status status) {
        super(message);
        this.status = status;
    }

    Status getStatus() {
        return status;
    }

    int getStatusCode() {
        return status.getStatusCode();
    }
}
