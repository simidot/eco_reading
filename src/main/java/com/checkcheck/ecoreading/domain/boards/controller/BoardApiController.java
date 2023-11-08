package com.checkcheck.ecoreading.domain.boards.controller;

import com.checkcheck.ecoreading.domain.boards.dto.BookDTO;
import com.checkcheck.ecoreading.domain.boards.dto.InsertBoardDTO;
import com.checkcheck.ecoreading.domain.boards.dto.InsertBookDTO;
import com.checkcheck.ecoreading.domain.boards.dto.InsertDeliveryDTO;
import com.checkcheck.ecoreading.domain.boards.service.BoardService;
import com.checkcheck.ecoreading.domain.boards.service.BookService;
import com.checkcheck.ecoreading.domain.boards.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Controller
@RequiredArgsConstructor
public class BoardApiController {

    private final BoardService boardService;
    private final BookService bookService;
    private final S3Service s3Service;

    // 등록 폼에서 input 가져와서 DB에 업로드
    @ResponseBody
    @PostMapping(value = "/board/new", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public String uploadBoard(@RequestParam("image") List<MultipartFile> multipartFiles,
                              InsertBookDTO bookDTO, InsertBoardDTO boardDTO, InsertDeliveryDTO deliveryDTO) {
        System.out.println(multipartFiles);
//        System.out.println(insertDTO.toString());
//        if(multipartFiles==null) {
//            //예외 던지기
//        }

        // 올린 이미지 파일의 경로 리스트 받아오기 >> todo: 리스트에서 하나하나 db에 넣어야함.
        boardService.uploadBoard(multipartFiles, bookDTO, boardDTO, deliveryDTO);

        //todo: db에 url 저장

        return null;
    }

    // 나눔글 등록시 책 검색하기
    @GetMapping("/board/bookSearch")
    public String search(@RequestParam String text, Model model) {
        List<BookDTO> books = bookService.searchBooks(text);
        System.out.println("검색결과: "+ books);
        model.addAttribute("books", books);
        return "/content/user/bookSearch";
    }

    // 나눔글 등록시 책 검색 결과 갖고오기
    @PostMapping("/board/bookSearch")
    public String fillBook(BookDTO bookDTO){
        System.out.println("북디티오: "+bookDTO);
        return "/content/user/boardAddForm";
    }
}
