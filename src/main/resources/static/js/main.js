function showAlertModal(movieId, movieTitle) {
    const modalElement = document.getElementById('alertModal');
    if (modalElement) {
        document.getElementById('modalTitle').innerText = movieTitle + ' 명당 알림';
        // 폼이 있다면 movieId를 hidden input에 설정하는 로직 추가 가능
        const modal = new bootstrap.Modal(modalElement);
        modal.show();
    }
}

function confirmAlert() {
    alert('알림 신청이 완료되었습니다! 명당 자리가 나오면 메일로 알려드릴게요.');
    bootstrap.Modal.getInstance(document.getElementById('alertModal')).hide();
}