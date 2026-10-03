/* SPDX-License-Identifier: LicenseRef-PocketInstall-Personal-1.0
 * This is an EFI application, not a Windows installer.
 * Only console, timer, watchdog and shutdown services are used.
 */
#include <efi.h>

/* GNU-EFI crt0 adapts the firmware ABI to this SysV entry point.
 * GNU_EFI_USE_MS_ABI makes protocol function pointers use the firmware ABI.
 */
EFI_STATUS efi_main(EFI_HANDLE image, EFI_SYSTEM_TABLE *st)
{
    EFI_BOOT_SERVICES *bs;
    EFI_EVENT timer = NULL;
    EFI_EVENT events[2];
    UINTN selected = 0;
    EFI_STATUS status;
    static CHAR16 banner[] =
        L"\r\nPocketInstall boot successful\r\n\r\n"
        L"UEFI x64 proof of concept\r\n"
        L"This program does not open or write any disk.\r\n"
        L"Confirm HTTP or TFTP delivery in the server log.\r\n\r\n"
        L"Press any key to shut down, or wait 30 seconds.\r\n";
    static CHAR16 stopping[] = L"\r\nPocketInstall: shutting down.\r\n";
    (void)image;

    if (st == NULL || st->ConOut == NULL || st->ConIn == NULL ||
        st->BootServices == NULL || st->RuntimeServices == NULL)
        return EFI_INVALID_PARAMETER;

    bs = st->BootServices;
    bs->SetWatchdogTimer(0, 0, 0, NULL);
    st->ConOut->ClearScreen(st->ConOut);
    st->ConOut->SetAttribute(st->ConOut, EFI_LIGHTGREEN | EFI_BACKGROUND_BLACK);
    st->ConOut->OutputString(st->ConOut, banner);
    st->ConIn->Reset(st->ConIn, FALSE);

    status = bs->CreateEvent(EVT_TIMER, TPL_APPLICATION, NULL, NULL, &timer);
    if (!EFI_ERROR(status)) {
        status = bs->SetTimer(timer, TimerRelative, 30ULL * 10000000ULL);
        if (!EFI_ERROR(status)) {
            events[0] = st->ConIn->WaitForKey;
            events[1] = timer;
            bs->WaitForEvent(2, events, &selected);
        } else {
            bs->Stall(30000000);
        }
        bs->CloseEvent(timer);
    } else {
        bs->Stall(30000000);
    }

    st->ConOut->OutputString(st->ConOut, stopping);
    st->RuntimeServices->ResetSystem(EfiResetShutdown, EFI_SUCCESS, 0, NULL);
    /* A conforming ResetSystem does not return. Do not fall through to disk boot
     * if a broken firmware returns: keep the application alive until power-off.
     */
    for (;;) bs->Stall(1000000);
}
