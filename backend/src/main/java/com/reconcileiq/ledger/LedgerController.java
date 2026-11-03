package com.reconcileiq.ledger;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerEntryRepository repository;
    private final LedgerImportService importService;

    public LedgerController(
            LedgerEntryRepository repository,
            LedgerImportService importService) {

        this.repository = repository;
        this.importService = importService;
    }

    @PostMapping(
            value = "/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public LedgerDtos.ImportResponse importCsv(
            @RequestParam("file") MultipartFile file) {

        return importService.importCsv(file);
    }

    @GetMapping
    public List<LedgerDtos.LedgerEntryResponse> listEntries() {

        return repository.findAll(
                        Sort.by(Sort.Direction.DESC, "id")
                )
                .stream()
                .map(LedgerDtos.LedgerEntryResponse::from)
                .toList();
    }
}