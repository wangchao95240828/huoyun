package com.dws.controller;

import com.dws.dto.IotRequest;
import com.dws.dto.IotResponse;
import com.dws.exception.IotException;
import com.dws.service.IotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("api/iot/warehouse")
public class DwsController {

    private final IotService iotService;

    public DwsController(IotService iotService) {
        this.iotService = iotService;
    }

    @PostMapping("/parcel")
    public IotResponse handleIotRequest(@RequestBody IotRequest request) {
        return iotService.process(request);
    }

    @ExceptionHandler(IotException.class)
    public ResponseEntity<IotResponse> handleIotException(IotException ex) {
        IotResponse response = IotResponse.builder()
                .status(0)
                .info(ex.getMessage())
                .action(ex.getSuggestedAction())
                .build();
        return ResponseEntity.ok(response);
    }
}
