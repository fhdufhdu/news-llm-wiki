package com.newswiki.service;

import org.springframework.stereotype.Service;

@Service
public class WikiService {
    public String pendingSummary() {
        return "수집과 위키 데이터 생성 작업이 완료되면 연결된 지식 베이스가 표시됩니다.";
    }
}
